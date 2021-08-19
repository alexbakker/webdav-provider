package me.alexbakker.webdav.provider

import android.content.Context
import me.alexbakker.webdav.data.Account
import me.alexbakker.webdav.data.CacheDao
import me.alexbakker.webdav.data.CacheEntry
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class WebDavCache (private val context: Context, private val dao: CacheDao) {
    private val roots: MutableMap<Int, WebDavFile> = HashMap()

    fun getRoot(account: Account): WebDavFile {
        var root = roots[account.id.toInt()]
        if (root != null) {
            return root
        }

        root = WebDavFile(account.rootPath, true)
        setRoot(account, root)
        return root
    }

    fun setRoot(account: Account, file: WebDavFile) {
        file.isRoot = true
        roots[account.id.toInt()] = file
    }

    fun get(account: Account, file: WebDavFile): Result {
        val entry = dao.getByPath(account.id, file.path)
            ?: return Result(status = Result.Status.MISS)

        if (entry.status == CacheEntry.Status.PENDING) {
            return Result(entry, Result.Status.PENDING)
        }

        if (!mapPathToCache(account.id, file.path).exists()) {
            return Result(entry, Result.Status.MISS)
        }

        // etag is the primary way to check for cache hits
        if (entry.etag != null) {
            if (entry.etag == file.etag) {
                return Result(entry, Result.Status.HIT)
            }

            return Result(entry, Result.Status.MISS)
        }

        // if no etag is present, the timestamp being equal is also considered a cache hit
        if (entry.lastModified != null && entry.lastModified == file.lastModified?.time) {
            return Result(entry, Result.Status.HIT)
        }

        return Result(status = Result.Status.MISS)
    }

    @Throws(IOException::class)
    fun startPut(account: Account, file: WebDavFile): Writer {
        val cacheFile = mapPathToCache(account.id, file.path)
        val cacheDir = cacheFile.parentFile!!
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val entry = CacheEntry(
            accountId = account.id,
            status = CacheEntry.Status.PENDING,
            path = file.path,
            etag = file.etag,
            contentLength = file.contentLength,
            lastModified = file.lastModified?.time
        )
        entry.id = dao.insert(entry)

        return Writer(entry, cacheFile)
    }

    fun mapPathToCache(accountId: Long, path: String): File {
        return File(File(context.cacheDir, accountId.toString()), path)
    }

    inner class Writer @Throws(IOException::class) constructor(
        private val entry: CacheEntry,
        private val file: File
    ) : Closeable {
        var done = false
            private set
        var broken = false
            private set

        private var closed = false
        val stream = FileOutputStream(file)

        @Throws(IOException::class)
        fun abort() {
            broken = true
            close()
        }

        @Throws(IOException::class)
        fun finish() {
            done = true
            close()
        }

        @Throws(IOException::class)
        override fun close() {
            if (!closed) {
                if (done && !broken) {
                    entry.status = CacheEntry.Status.DONE
                    dao.update(entry)
                } else {
                    dao.delete(entry)
                    file.delete()
                }

                closed = true
                stream.close()
            }
        }
    }

    data class Result(
        val entry: CacheEntry? = null,
        val status: Status
    ) {
        enum class Status {
            HIT, PENDING, MISS
        }
    }
}
