package me.alexbakker.webdav.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ApplicationComponent
import kotlinx.coroutines.*
import me.alexbakker.webdav.R
import me.alexbakker.webdav.settings.Account
import me.alexbakker.webdav.settings.Settings
import me.alexbakker.webdav.settings.byUUID
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.*

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

    private lateinit var settings: Settings

    override fun onCreate(): Boolean {
        Log.d(TAG, "onCreate()")

        val entryPoint = EntryPointAccessors.fromApplication(
            context!!.applicationContext,
            WebDavEntryPoint::class.java
        )
        settings = entryPoint.provideSettings()

        for (account in settings.accounts) {
            refreshAccount(account)
        }

        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        Log.d(TAG, "queryRoots()")

        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)

        result.apply {
            for (account in settings.accounts) {
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
                parent.replaceWith(file)
                parent = file
            }

            result.apply {
                for (file in parent.children) {
                    includeFile(this, account, file)
                }
            }
        }

        return result
    }

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

        val cacheFile = File(requireContext().cacheDir, file.path)
        val cacheDir = cacheFile.parentFile
        if (cacheDir == null || (!cacheDir.exists() && !cacheDir.mkdirs())) {
            return null
        }

        when (mode) {
            "r" -> {
                val success = runBlocking {
                    withContext(Dispatchers.IO) {
                        //val url = account.buildURL(file)
                        val res = account.client.get(file.path)
                        if (res.isSuccessful) {
                            res.body!!.use { inStream ->
                                FileOutputStream(cacheFile).use { outStream ->
                                    inStream.copyTo(outStream)
                                }
                            }
                        }
                        res.isSuccessful
                    }
                }

                return if (success) {
                    ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
                } else {
                    null
                }
            }
            "w" -> {
                val pipe = ParcelFileDescriptor.createReliablePipe()
                val inStream = ParcelFileDescriptor.AutoCloseInputStream(pipe[0])
                val job = GlobalScope.launch(Dispatchers.IO) {
                    inStream.use {
                        //val url = account.buildURL(file)
                        val res = account.client.put(file.path, inStream, contentType = file.contentType)
                        if (!res.isSuccessful) {
                            Log.e(TAG, "Error: ${res.error?.message}")
                        }
                    }
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
                val res = account.client.put(path)
                Log.d(TAG, "createDocument(), success=${res.isSuccessful}, documentId=$documentId, mimeType=$mimeType, displayName=$displayName")
                res
            }
        }

        if (res.isSuccessful) {
            val file = WebDavFile(path, isDirectory, contentType = mimeType)
            file.parent = dir
            dir.children.add(file)
            return "/${account.uuid}${file.path}"
        }

        return null
    }

    override fun deleteDocument(documentId: String?) {
        Log.d(TAG, "deleteDocument(), documentId=$documentId")

        val (account, file) = findDocument(documentId!!)
        if (file == null) {
            throw FileNotFoundException(documentId)
        }

        runBlocking {
            val job = GlobalScope.launch(Dispatchers.IO) {
                // val url = account.buildURL(file)
                val res = account.client.delete(file.path)
                Log.i(
                    TAG,
                    "deleteDocument(), documentId=$documentId, success=${res.isSuccessful}, message=${res.error?.message}"
                )
            }
            job.join()
        }

        if (file.parent != null) {
            file.parent!!.children.remove(file)
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        Log.d(TAG, "isChildDocument(), parentDocumentId=$parentDocumentId, documentId=$documentId")

        val (account, file) = findDocument(documentId)
        val (parentUUID, parentPath) = parseDocumentId(parentDocumentId)

        return account.uuid == parentUUID && file?.parent?.path == parentPath
    }

    private fun refreshAccount(account: Account) {
        GlobalScope.launch(Dispatchers.IO) {
            val result = account.client.propFind("/")
            if (result.isSuccessful) {
                account.root = result.body!!
            }
        }
    }

    private fun findDocument(documentId: String): Pair<Account, WebDavFile?> {
        val (uuid, path) = parseDocumentId(documentId)
        val account = settings.accounts.byUUID(uuid)
        val file = account.root.findByPath(path)
        return Pair(account, file)
    }

    private fun parseDocumentId(documentId: String): Pair<UUID, String> {
        val parts = documentId.split("/")
        val uuid = UUID.fromString(parts[1])
        val path = parts.drop(2).joinToString("/", prefix = "/")
        return Pair(uuid, path)
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
            add(Document.COLUMN_DOCUMENT_ID, "/${account.uuid}${file.path}")
            add(Document.COLUMN_MIME_TYPE, if (file.isDirectory) Document.MIME_TYPE_DIR else file.contentType)
            add(Document.COLUMN_DISPLAY_NAME, file.name)
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified?.time)
            add(Document.COLUMN_FLAGS, flags)
            add(Document.COLUMN_SIZE, file.contentLength)
        }
    }

    private fun includeAccount(cursor: MatrixCursor, account: Account) {
        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, account.uuid)
            add(Root.COLUMN_SUMMARY, account.name)
            add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)
            add(Root.COLUMN_TITLE, "WebDAV")
            add(Root.COLUMN_DOCUMENT_ID, "/${account.uuid}/")
            add(Root.COLUMN_MIME_TYPES, null)
            add(Root.COLUMN_AVAILABLE_BYTES, account.root.quotaAvailableBytes)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                var avail: Int? = null
                if (account.root.quotaUsedBytes != null && account.root.quotaAvailableBytes != null) {
                    avail = account.root.quotaUsedBytes!! + account.root.quotaAvailableBytes!!
                }
                add(Root.COLUMN_CAPACITY_BYTES, avail)
            }
            add(Root.COLUMN_ICON, R.drawable.ic_launcher_foreground)
        }
    }

    @EntryPoint
    @InstallIn(ApplicationComponent::class)
    interface WebDavEntryPoint {
        fun provideSettings(): Settings
    }
}
