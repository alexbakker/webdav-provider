package me.alexbakker.webdav.provider

import android.os.ProxyFileDescriptorCallback
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.util.*

class WebDavReadProxyCallback(
    private val client: WebDavClient,
    private val file: WebDavFile,
    private val cacheWriter: WebDavCache.Writer? = null
) : ProxyFileDescriptorCallback() {
    private var nextOffset = 0L
    private var inStream: InputStream? = null
    private val contentLength: Long = file.contentLength!!.toLong()

    private val uuid = UUID.randomUUID()
    private val TAG: String = "${javaClass.simpleName}(uuid=$uuid)"

    init {
        Log.d(TAG, "init(file=${file.path}, contentLength=${contentLength})")
    }

    @Throws(ErrnoException::class)
    override fun onGetSize(): Long {
        Log.d(TAG, "onGetSize(contentLength=${contentLength})")
        return contentLength
    }

    @Throws(ErrnoException::class)
    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
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
                    cacheWriter.abort()
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
        val res = runBlocking { client.get(file.davPath, offset) }
        if (!res.isSuccessful) {
            throw ErrnoException("openWebDavStream", OsConstants.EBADF)
        }
        return res.body!!
    }
}