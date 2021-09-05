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
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import me.alexbakker.webdav.BuildConfig
import me.alexbakker.webdav.R
import me.alexbakker.webdav.data.Account
import me.alexbakker.webdav.data.AccountDao
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
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

    private val accountDao: AccountDao
        get() { return entryPoint.provideAccountDao() }

    private val cache: WebDavCache
        get() { return entryPoint.provideWebDavCache() }

    private val entryPoint: WebDavEntryPoint
        get() { return EntryPoints.get(mustGetContext(), WebDavEntryPoint::class.java) }

    private lateinit var looper: WebDavFileReadCallbackLooper
    private lateinit var storageManager: StorageManager

    override fun onCreate(): Boolean {
        Log.d(TAG, "onCreate()")

        val context = mustGetContext()
        looper = WebDavFileReadCallbackLooper()
        storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        Log.d(TAG, "queryRoots()")

        val result = WebDavCursor(projection ?: DEFAULT_ROOT_PROJECTION)

        result.apply {
            for (account in accountDao.getAll()) {
                includeAccount(this, account)
            }
        }

        return result
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        Log.d(TAG, "queryDocument(documentId=$documentId)")

        val result = WebDavCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        result.apply {
            val (account, _, file) = findDocument(documentId!!)
            if (file != null) {
                includeFile(this, account, file)
            }
        }

        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        Log.d(TAG, "queryChildDocuments(parentDocumentId=$parentDocumentId, sortOrder=$sortOrder)")

        val result = WebDavCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        val (account, parentPath) = lookupDocumentId(parentDocumentId)
        val res = runBlocking(Dispatchers.IO) { account.client.propFind(parentPath) }
        if (res.isSuccessful) {
            val parentFile = res.body!!
            cache.setFileMeta(account, parentFile)

            result.apply {
                for (file in parentFile.children) {
                    if (!file.isPending) {
                        includeFile(this, account, file)
                    }
                }

                val notifyUri = buildDocumentUri(account, parentFile)
                setNotificationUri(mustGetContext().contentResolver, notifyUri)
            }
        } else {
            result.errorMsg = mustGetContext().getString(R.string.error_list_dir_contents, parentPath.toString(), res.error)
        }

        return result
    }

    @DelicateCoroutinesApi
    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor? {
        Log.d(TAG, "openDocument(documentId=$documentId, mode=$mode)")

        val (account, _, file) = findDocument(documentId)
        if (file == null) {
            throw FileNotFoundException(documentId)
        }

        val accessMode = ParcelFileDescriptor.parseMode(mode)
        when (mode) {
            "r" -> {
                synchronized(cache) {
                    val cacheResult = cache.get(account, file)
                    return when (cacheResult.status) {
                        WebDavCache.Result.Status.HIT -> {
                            val cacheFile = cache.mapPathToCache(account.id, Paths.get(cacheResult.entry!!.path))
                            ParcelFileDescriptor.open(cacheFile.toFile(), accessMode)
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
                val inDesc = pipe[0]
                val inStream = ParcelFileDescriptor.AutoCloseInputStream(inDesc)
                val job = GlobalScope.launch(Dispatchers.IO) {
                    val res = inStream.use {
                        account.client.putFile(
                            file.path.toString(),
                            inStream,
                            contentType = file.contentType,
                            contentLength = inDesc.statSize
                        )
                    }

                    if (res.isSuccessful) {
                        val propRes = account.client.propFindFile(file.path)
                        if (propRes.isSuccessful) {
                            val parent = file.parent!!
                            parent.children.remove(file)
                            parent.children.add(propRes.body!!)
                        } else {
                            Log.e(TAG, "openDocument(documentId=$documentId, mode=$mode) propFind failed: ${propRes.error?.message}\")")
                        }
                    } else {
                        Log.e(TAG, "openDocument(documentId=$documentId, mode=$mode) upload failed: ${res.error?.message}\")")
                    }

                    val notifyUri = buildDocumentUri(account, file.parent!!)
                    mustGetContext().contentResolver.notifyChange(notifyUri, null, 0)
                }

                signal?.setOnCancelListener {
                    job.cancel("openDocument(documentId=$documentId, mode=$mode): cancellation signal received")
                }

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
        Log.d(TAG, "createDocument(documentId=$documentId, mimeType=$mimeType, displayName=$displayName)")

        val (account, _, dir) = findDocument(documentId)
        if (dir == null || !dir.isDirectory) {
            throw FileNotFoundException(documentId)
        }

        val path = dir.path.resolve(displayName)
        val isDirectory = mimeType.equals(Document.MIME_TYPE_DIR, ignoreCase = true)

        var resDocumentId: String? = null
        if (isDirectory) {
            val res = runBlocking(Dispatchers.IO) { account.client.putDir(path.toString()) }
            if (res.isSuccessful) {
                val file = WebDavFile(path, true, contentType = mimeType)
                file.parent = dir
                dir.children.add(file)
                cache.setFileMeta(account, file)

                val notifyUri = buildDocumentUri(account, file.parent!!)
                mustGetContext().contentResolver.notifyChange(notifyUri, null, 0)

                resDocumentId = buildDocumentId(account, file)
            }
        } else {
            val file = WebDavFile(path, false, contentType = mimeType, isPending = true)
            file.parent = dir
            dir.children.add(file)

            resDocumentId = buildDocumentId(account, file)
        }

        Log.d(TAG, "createDocument(documentId=$documentId, mimeType=$mimeType, displayName=$displayName): success=${resDocumentId != null}")
        return resDocumentId
    }

    override fun deleteDocument(documentId: String?) {
        Log.d(TAG, "deleteDocument(documentId=$documentId)")
        val (account, _, file) = findDocument(documentId!!)
        if (file == null) {
            throw FileNotFoundException(documentId)
        }

        val res = runBlocking(Dispatchers.IO) { account.client.delete(file.path.toString()) }
        Log.d(TAG, "deleteDocument(documentId=$documentId): success=${res.isSuccessful}, message=${res.error?.message}")

        if (res.isSuccessful) {
            cache.removeFileMeta(account, file.path)

            if (file.parent != null) {
                file.parent!!.children.remove(file)
            }

            val notifyUri = buildDocumentUri(account, file.path.parent)
            mustGetContext().contentResolver.notifyChange(notifyUri, null, 0)
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        // NOTE: This does not check whether the file actually exists
        val (account, filePath) = lookupDocumentId(documentId)
        val (parentAccount, parentPath) = lookupDocumentId(parentDocumentId)
        val isChild = account.id == parentAccount.id && filePath.startsWith(parentPath)

        Log.d(TAG, "isChildDocument(parentDocumentId=$parentDocumentId, documentId=$documentId): isChild=$isChild")
        return isChild
    }

    /**
     * Tries to find the document with the given ID. If metadata for this document is
     * not available in the cache, a PROPFIND request is performed for the parent path.
     */
    private fun findDocument(documentId: String): WebDavDocument {
        val doc = findCachedDocument(documentId)
        if (doc.file != null) {
            return doc
        }

        val isRoot = doc.account.rootPath == doc.path
        val res = runBlocking(Dispatchers.IO) {
            val path = if (isRoot) doc.path else doc.path.parent
            doc.account.client.propFind(path)
        }
        if (res.isSuccessful) {
            val resFile = res.body!!
            if (resFile.isDirectory) {
                cache.setFileMeta(doc.account, resFile)
            }

            val file = if (isRoot) {
                resFile
            } else {
                resFile.children.find { f -> f.path == doc.path }
            }

            if (file != null) {
                return doc.copy(file = file)
            }
        }

        return doc
    }

    /**
     * Tries to find the document with the given ID and its metadata in the cache.
     */
    private fun findCachedDocument(documentId: String): WebDavDocument {
        val (account, path) = lookupDocumentId(documentId)
        val file = cache.getFileMeta(account, path)
        return WebDavDocument(account, path, file)
    }

    private fun lookupDocumentId(documentId: String): Pair<Account, Path> {
        val (accountId, path) = parseDocumentId(documentId)
        val account = accountDao.getById(accountId)
            ?: throw IllegalArgumentException("Invalid document ID: '$documentId' (Account $accountId not found")

        return Pair(account, path)
    }

    private data class WebDavDocument(
        val account: Account,
        val path: Path,
        val file: WebDavFile? = null,
    )

    private fun includeFile(cursor: WebDavCursor, account: Account, file: WebDavFile) {
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

    private fun includeAccount(cursor: WebDavCursor, account: Account) {
        val root = cache.getFileMeta(account, account.rootPath)

        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, account.id)
            add(Root.COLUMN_SUMMARY, account.name)
            add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)
            add(Root.COLUMN_TITLE, "WebDAV")
            add(Root.COLUMN_DOCUMENT_ID, buildDocumentId(account, account.rootPath))
            add(Root.COLUMN_MIME_TYPES, null)
            add(Root.COLUMN_AVAILABLE_BYTES, root?.quotaAvailableBytes)
            add(Root.COLUMN_ICON, R.mipmap.ic_launcher)

            var avail: Long? = null
            if (root?.quotaUsedBytes != null && root.quotaAvailableBytes != null) {
                avail = root.quotaUsedBytes!! + root.quotaAvailableBytes!!
            }
            add(Root.COLUMN_CAPACITY_BYTES, avail)
        }
    }

    private fun mustGetContext(): Context {
        return context ?: throw IllegalStateException("Cannot find context from the provider.")
    }

    private fun getHandler(): Handler {
        return Handler(looper.looper)
    }

    companion object {
        fun parseDocumentId(documentId: String): Pair<Long, Path> {
            val parts = documentId.split("/")
            if (parts.size < 3) {
                throw IllegalArgumentException("Invalid document ID: '$documentId'")
            }

            val id = try {
                parts[1].toLong()
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Invalid document ID: '$documentId' (Bad account ID: ${parts[1]}")
            }

            val path = Paths.get(parts.drop(2).joinToString("/", prefix = "/"))
            return Pair(id, path)
        }

        fun buildDocumentId(account: Account, path: Path): String {
            return "/${account.id}${path}"
        }

        fun buildDocumentId(account: Account, file: WebDavFile): String {
            return buildDocumentId(account, file.path)
        }

        fun buildDocumentUri(documentId: String): Uri {
            return DocumentsContract.buildDocumentUri(BuildConfig.PROVIDER_AUTHORITY, documentId)
        }

        fun buildTreeDocumentUri(documentId: String): Uri {
            return DocumentsContract.buildTreeDocumentUri(BuildConfig.PROVIDER_AUTHORITY, documentId)
        }

        fun buildDocumentUri(account: Account, file: WebDavFile): Uri {
            return buildDocumentUri(buildDocumentId(account, file))
        }

        fun buildDocumentUri(account: Account, path: Path): Uri {
            return buildDocumentUri(buildDocumentId(account, path))
        }

        /**
         * Notify any observers that the roots of our provider have changed.
         */
        fun notifyChangeRoots(context: Context) {
            val rootsUri = DocumentsContract.buildRootsUri(BuildConfig.PROVIDER_AUTHORITY)
            context.contentResolver.notifyChange(rootsUri, null, 0)
        }
    }

    private class WebDavCursor(
        columnNames: Array<out String>,
        var infoMsg: String? = null,
        var errorMsg: String? = null
    ) : MatrixCursor(columnNames) {
        override fun getExtras(): Bundle {
            val bundle = Bundle()
            if (infoMsg != null) {
                bundle.putString(DocumentsContract.EXTRA_INFO, infoMsg)
            }
            if (errorMsg != null) {
                bundle.putString(DocumentsContract.EXTRA_ERROR, errorMsg)
            }
            return bundle
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WebDavEntryPoint {
        fun provideAccountDao(): AccountDao
        fun provideWebDavCache(): WebDavCache
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
            val res = runBlocking { client.get(file.path.toString(), offset) }
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
