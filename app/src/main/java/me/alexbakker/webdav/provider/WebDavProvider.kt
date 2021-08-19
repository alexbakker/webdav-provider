package me.alexbakker.webdav.provider

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.*
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import me.alexbakker.webdav.BuildConfig
import me.alexbakker.webdav.R
import me.alexbakker.webdav.data.Account
import me.alexbakker.webdav.data.AccountDao
import me.alexbakker.webdav.data.CacheDao
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.*
import java.util.concurrent.CountDownLatch

class WebDavProvider : DocumentsProvider() {
    private val TAG: String = WebDavProvider::class.java.simpleName

    private val DEFAULT_ROOT_PROJECTION = arrayOf(
        Root.COLUMN_ROOT_ID,
        Root.COLUMN_MIME_TYPES,
        Root.COLUMN_FLAGS,
        Root.COLUMN_ICON,
        Root.COLUMN_TITLE,
        Root.COLUMN_SUMMARY,
        Root.COLUMN_DOCUMENT_ID,
        Root.COLUMN_AVAILABLE_BYTES
    )

    private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_FLAGS,
        Document.COLUMN_SIZE
    )

    private lateinit var accountDao: AccountDao
    private lateinit var cache: WebDavCache
    private lateinit var looper: WebDavFileReadCallbackLooper
    private lateinit var storageManager: StorageManager

    @DelicateCoroutinesApi
    override fun onCreate(): Boolean {
        Log.d(TAG, "onCreate()")

        val entryPoint = EntryPointAccessors.fromApplication(
            context!!.applicationContext,
            WebDavEntryPoint::class.java
        )
        accountDao = entryPoint.provideAccountDao()
        cache = WebDavCache(context!!.applicationContext, entryPoint.provideCacheDao())
        looper = WebDavFileReadCallbackLooper()
        storageManager = context!!.getSystemService(Context.STORAGE_SERVICE) as StorageManager

        for (account in accountDao.getAll()) {
            refreshAccount(account)
        }

        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        Log.d(TAG, "queryRoots()")

        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)

        result.apply {
            for (account in accountDao.getAll()) {
                includeAccount(this, account)
            }
        }

        return result
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        Log.d(TAG, "queryDocument(), documentId=$documentId")

        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        result.apply {
            val (account, file) = findDocument(documentId!!)
            if (file != null) {
                includeFile(this, account, file)
            }
        }

        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        Log.d(TAG, "queryChildDocuments(), parentDocumentId=$parentDocumentId, sortOrder=$sortOrder")

        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        var (account, parent) = findDocument(parentDocumentId!!)
        if (parent != null) {
            val res = runBlocking {
                withContext(Dispatchers.IO) {
                    account.client.propFind(parent!!.path)
                }
            }

            if (res.isSuccessful) {
                val file = res.body!!
                if (parent.isRoot) {
                    cache.setRoot(account, file)
                } else {
                    parent.replaceWith(file)
                }
                parent = file
            }

            result.apply {
                for (file in parent.children) {
                    includeFile(this, account, file)
                }

                val notifyUri = buildDocumentUri(account, parent)
                setNotificationUri(mustGetContext().contentResolver, notifyUri)
            }
        }

        return result
    }

    @DelicateCoroutinesApi
    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor? {
        Log.d(TAG, "openDocument(), documentId=$documentId, mode=$mode")

        val (account, file) = findDocument(documentId)
        if (file == null) {
            return null
        }

        val accessMode = ParcelFileDescriptor.parseMode(mode)
        when (mode) {
            "r" -> {
                synchronized(cache) {
                    val cacheResult = cache.get(account, file)
                    return when (cacheResult.status) {
                        WebDavCache.Result.Status.HIT -> {
                            val cacheFile = cache.mapPathToCache(account.id, cacheResult.entry!!.path)
                            ParcelFileDescriptor.open(cacheFile, accessMode);
                        }
                        WebDavCache.Result.Status.PENDING -> {
                            val callback = WebDavFileReadProxyCallback(account, file)
                            storageManager.openProxyFileDescriptor(accessMode, callback, getHandler())
                        }
                        WebDavCache.Result.Status.MISS -> {
                            val tryCache = file.contentLength != null
                                    && file.contentLength!! <= (account.maxCacheFileSize * 1_000_000)
                            val callback = WebDavFileReadProxyCallback(account, file, tryCache)
                            storageManager.openProxyFileDescriptor(accessMode, callback, getHandler())
                        }
                    }
                }
            }
            "w" -> {
                val pipe = ParcelFileDescriptor.createReliablePipe()
                val inStream = ParcelFileDescriptor.AutoCloseInputStream(pipe[0])
                val job = GlobalScope.launch(Dispatchers.IO) {
                    inStream.use {
                        val res = account.client.putFile(file.path, inStream, contentType = file.contentType)
                        if (!res.isSuccessful) {
                            Log.e(TAG, "Error: ${res.error?.message}")
                        }
                    }

                    val notifyUri = buildDocumentUri(account, file.parent!!)
                    mustGetContext().contentResolver.notifyChange(notifyUri, null, 0)
                }

                signal?.setOnCancelListener { job.cancel("openDocument() cancellation signal received") }
                return pipe[1]
            }
            else -> {
                throw UnsupportedOperationException("Mode '$mode' is not supported")
            }
        }
    }

    @Throws(FileNotFoundException::class)
    override fun createDocument(
        documentId: String,
        mimeType: String?,
        displayName: String?
    ): String? {
        Log.d(TAG, "createDocument(), documentId=$documentId, mimeType=$mimeType, displayName=$displayName")

        val (account, dir) = findDocument(documentId)
        if (dir == null || !dir.isDirectory) {
            throw FileNotFoundException(documentId)
        }

        var path = dir.path + displayName
        val isDirectory = mimeType.equals(Document.MIME_TYPE_DIR, ignoreCase = true)
        if (isDirectory) {
            path += "/"
        }

        val res = runBlocking {
            withContext(Dispatchers.IO) {
                val res = if (isDirectory) {
                    account.client.putDir(path)
                } else {
                    account.client.putFile(path)
                }
                Log.d(TAG, "createDocument(), success=${res.isSuccessful}, documentId=$documentId, mimeType=$mimeType, displayName=$displayName")
                res
            }
        }

        if (res.isSuccessful) {
            val file = WebDavFile(path, isDirectory, contentType = mimeType)
            file.parent = dir
            dir.children.add(file)

            val notifyUri = buildDocumentUri(account, file.parent!!)
            mustGetContext().contentResolver.notifyChange(notifyUri, null, 0)

            return buildDocumentId(account, file)
        }

        return null
    }

    override fun deleteDocument(documentId: String?) {
        Log.d(TAG, "deleteDocument(), documentId=$documentId")

        val (account, file) = findDocument(documentId!!)
        if (file == null) {
            throw FileNotFoundException(documentId)
        }

        val res = runBlocking {
            withContext(Dispatchers.IO) {
                account.client.delete(file.path)
            }
        }
        Log.d(TAG, "deleteDocument(), documentId=$documentId, success=${res.isSuccessful}, message=${res.error?.message}")

        if (res.isSuccessful) {
            if (file.parent != null) {
                file.parent!!.children.remove(file)

                val notifyUri = buildDocumentUri(account, file.parent!!)
                mustGetContext().contentResolver.notifyChange(notifyUri, null, 0)
            }
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        Log.d(TAG, "isChildDocument(), parentDocumentId=$parentDocumentId, documentId=$documentId")

        val (account, file) = findDocument(documentId)
        val (parentId, parentPath) = parseDocumentId(parentDocumentId)

        return account.id == parentId && file?.parent?.path == parentPath
    }

    @DelicateCoroutinesApi
    private fun refreshAccount(account: Account) {
        GlobalScope.launch(Dispatchers.IO) {
            val root = cache.getRoot(account)
            val result = account.client.propFind(root.path)
            if (result.isSuccessful) {
                cache.setRoot(account, result.body!!)
            }
        }
    }

    private fun findDocument(documentId: String): Pair<Account, WebDavFile?> {
        val (id, path) = parseDocumentId(documentId)
        val account = accountDao.getById(id)
        val file = cache.getRoot(account).findByPath(path)
        return Pair(account, file)
    }

    private fun parseDocumentId(documentId: String): Pair<Long, String> {
        val parts = documentId.split("/")
        val id = parts[1].toLong()
        val path = parts.drop(2).joinToString("/", prefix = "/")
        return Pair(id, path)
    }

    private fun includeFile(cursor: MatrixCursor, account: Account, file: WebDavFile) {
        var flags = 0
        if (file.isDirectory) {
            flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        }
        if (file.writable) {
            flags = flags or Document.FLAG_SUPPORTS_WRITE
            flags = flags or Document.FLAG_SUPPORTS_DELETE
        }

        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, buildDocumentId(account, file))
            add(Document.COLUMN_MIME_TYPE, if (file.isDirectory) Document.MIME_TYPE_DIR else file.contentType)
            add(Document.COLUMN_DISPLAY_NAME, file.decodedName)
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified?.time)
            add(Document.COLUMN_FLAGS, flags)
            add(Document.COLUMN_SIZE, file.contentLength)
        }
    }

    private fun includeAccount(cursor: MatrixCursor, account: Account) {
        val root = cache.getRoot(account)

        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, account.id)
            add(Root.COLUMN_SUMMARY, account.name)
            add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)
            add(Root.COLUMN_TITLE, "WebDAV")
            add(Root.COLUMN_DOCUMENT_ID, buildDocumentId(account, root))
            add(Root.COLUMN_MIME_TYPES, null)
            add(Root.COLUMN_AVAILABLE_BYTES, root.quotaAvailableBytes)
            add(Root.COLUMN_ICON, R.mipmap.ic_launcher)

            var avail: Long? = null
            if (root.quotaUsedBytes != null && root.quotaAvailableBytes != null) {
                avail = root.quotaUsedBytes!! + root.quotaAvailableBytes!!
            }
            add(Root.COLUMN_CAPACITY_BYTES, avail)
        }
    }

    private fun buildDocumentId(account: Account, file: WebDavFile): String {
        return "/${account.id}${file.path}"
    }

    private fun buildDocumentUri(documentId: String): Uri {
        return DocumentsContract.buildDocumentUri(BuildConfig.PROVIDER_AUTHORITY, documentId)
    }

    private fun buildDocumentUri(account: Account, file: WebDavFile): Uri {
        return buildDocumentUri(buildDocumentId(account, file))
    }

    private fun mustGetContext(): Context {
        return context ?: throw IllegalStateException("Cannot find context from the provider.")
    }

    private fun getHandler(): Handler {
        return Handler(looper.looper)
    }

    companion object {
        /**
         * Notify any observers that the roots of our provider have changed.
         */
        fun notifyChangeRoots(context: Context) {
            val rootsUri = DocumentsContract.buildRootsUri(BuildConfig.PROVIDER_AUTHORITY)
            context.contentResolver.notifyChange(rootsUri, null, 0)
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WebDavEntryPoint {
        fun provideAccountDao(): AccountDao
        fun provideCacheDao(): CacheDao
    }

    inner class WebDavFileReadProxyCallback : ProxyFileDescriptorCallback {
        private var cacheWriter: WebDavCache.Writer?
        private var inStream: InputStream? = null
        private val client: WebDavClient
        private val file: WebDavFile
        private val contentLength: Long
        private var nextOffset = 0L

        private val uuid = UUID.randomUUID()
        private val TAG: String = "WebDavFileReadProxyCallback(uuid=$uuid)"

        constructor(account: Account, webDavFile: WebDavFile, tryCache: Boolean) : super() {
            client = account.client
            file = webDavFile
            contentLength = file.contentLength!!.toLong()
            cacheWriter = if (tryCache) cache.startPut(account, file) else null

            logInit()
        }

        constructor(account: Account, file: WebDavFile) : this(account, file, false) {
            logInit()
        }

        private fun logInit() {
            Log.d(TAG, "init(file=${file.path}, contentLength=${contentLength})")
        }

        @Throws(ErrnoException::class)
        override fun onGetSize(): Long {
            Log.d(TAG, "onGetSize(contentLength=${contentLength})")
            return contentLength
        }

        @Throws(ErrnoException::class)
        override fun onRead(offset: Long, size: Int, data: ByteArray?): Int {
            Log.d(TAG, "onRead(offset=$offset, size=$size)")
            val inStream = getStream(offset)

            var res = 0
            while (true) {
                val read = inStream.read(data, res, size - res)
                if (read == -1) {
                    break
                }

                res += read
                if (res == size) {
                    break
                }
            }

            nextOffset = offset + size
            cacheWriter?.let {
                if (!it.broken) {
                    it.stream.write(data, 0, res)
                    if (contentLength == offset + res) {
                        it.finish()
                    }
                }
            }

            return res
        }

        override fun onRelease() {
            Log.d(TAG, "onRelease()")
            inStream?.close()
            cacheWriter?.close()
        }

        private fun getStream(offset: Long): InputStream {
            val res = when {
                inStream == null -> {
                    Log.d(TAG, "Opening stream at: offset=$offset")

                    // if the caller does not start streaming at 0, give up on trying to cache the file
                    if (cacheWriter != null && offset != 0L) {
                        cacheWriter!!.abort()
                    }

                    openWebDavStream(offset)
                }
                nextOffset != offset -> {
                    Log.w(TAG, "Unexpected offset: offset=$offset, expOffset=$nextOffset")
                    inStream!!.close()

                    // if the caller starts seeking in the stream, give up on trying to cache the file
                    cacheWriter?.abort()

                    Log.d(TAG, "Reopening stream at: offset=$offset")
                    openWebDavStream(offset)
                }
                else -> {
                    inStream!!
                }
            }

            inStream = res
            return res
        }

        private fun openWebDavStream(offset: Long): InputStream {
            val res = runBlocking { client.get(file.path, offset) }
            if (!res.isSuccessful) {
                throw ErrnoException("openWebDavStream", OsConstants.EBADF)
            }
            return res.body!!
        }
    }

    class WebDavFileReadCallbackLooper {
        lateinit var looper: Looper
        private val thread = Thread {
            Looper.prepare()
            looper = Looper.myLooper()!!
            latch.countDown()
            Looper.loop()
        }
        private val latch: CountDownLatch = CountDownLatch(1)

        init {
            try {
                thread.start()
                latch.await()
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }
    }
}
