package me.alexbakker.webdav.provider

import android.content.Context
import me.alexbakker.webdav.data.Account
import me.alexbakker.webdav.data.CacheDao
import me.alexbakker.webdav.data.CacheEntry
import java.io.Closeable
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.relativeTo

class WebDavCache (private val context: Context, private val dao: CacheDao) {
    private val fsCache: MutableMap<Long, MutableMap<Path, WebDavFile>> = HashMap()

    init {
        // clear any pending cache entries
        dao.deletePending()
    }

    fun getFileMeta(account: Account, path: Path): WebDavFile? {
        val cache = getFileMetaCache(account)
        val file = cache[path]
        if (file != null) {
            return file
        }

        if (path.parent != null) {
            val parentFile = cache[path.parent]
            if (parentFile != null) {
                // only return the cached metadata if the given path does not refer to a directory
                val childFile = parentFile.children.find { f -> f.path == path }
                if (childFile != null && !childFile.isDirectory) {
                    return childFile
                }
            }
        }

        return null
    }

    fun setFileMeta(account: Account, file: WebDavFile) {
        if (!file.isDirectory) {
            throw IllegalArgumentException("${file.path} is not a directory")
        }

        val cache = getFileMetaCache(account)
        cache[file.path] = file
    }

    fun removeFileMeta(account: Account, path: Path) {
        val cache = getFileMetaCache(account)
        cache.remove(path)
    }

    fun clearFileMeta(account: Account) {
        getFileMetaCache(account).clear()
    }

    private fun getFileMetaCache(account: Account): MutableMap<Path, WebDavFile> {
        var cache = fsCache[account.id]
        if (cache == null) {
            cache = HashMap()
            fsCache[account.id] = cache
        }

        return cache
    }

    fun get(account: Account, file: WebDavFile): Result {
        val entry = dao.getByPath(account.id, file.path.toString())
            ?: return Result(status = Result.Status.MISS)

        if (entry.status == CacheEntry.Status.PENDING) {
            return Result(entry, Result.Status.PENDING)
        }

        val cachePath = mapPathToCache(account.id, file.path)
        if (!Files.exists(cachePath)) {
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
        val cacheDir = cacheFile.parent!!
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir)
        }

        val entry = CacheEntry(
            accountId = account.id,
            status = CacheEntry.Status.PENDING,
            path = file.path.toString(),
            etag = file.etag,
            contentLength = file.contentLength,
            lastModified = file.lastModified?.time
        )
        entry.id = dao.insert(entry)

        return Writer(entry, cacheFile)
    }

    fun mapPathToCache(accountId: Long, path: Path): Path {
        val cacheDir = Paths.get(context.cacheDir.toString(), accountId.toString())
        return cacheDir.resolve(path.relativeTo(path.root))
    }

    inner class Writer @Throws(IOException::class) constructor(
        private val entry: CacheEntry,
        private val path: Path
    ) : Closeable {
        var done = false
            private set
        var broken = false
            private set
        private val file = path.toFile()

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
