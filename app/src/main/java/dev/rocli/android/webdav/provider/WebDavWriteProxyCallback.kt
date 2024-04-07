package dev.rocli.android.webdav.provider

import android.os.ProxyFileDescriptorCallback
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.BufferedSink
import okio.IOException
import okio.Pipe
import okio.buffer
import java.lang.Long.min
import java.util.UUID

class WebDavWriteProxyCallback(
    private val client: WebDavClient,
    private val file: WebDavFile,
    private val onSuccess: (WebDavFile) -> Unit,
    private val onFail: () -> Unit,
) : ProxyFileDescriptorCallback() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private var pipe: Pipe? = null
    private var sink: BufferedSink? = null

    private var contentLength = 0L
    private var nextOffset = 0L

    private val uuid = UUID.randomUUID()
    private val TAG: String = "${javaClass.simpleName}(uuid=$uuid)"

    init {
        Log.d(TAG, "init(file=${file.path})")
    }

    override fun onGetSize(): Long {
        Log.d(TAG, "onGetSize(contentLength=${contentLength})")
        return contentLength
    }

    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int {
        Log.d(TAG, "onWrite(offset=$offset, size=$size)")

        if (nextOffset != offset) {
            throw ErrnoException(
                "onWrite",
                OsConstants.ENOTSUP,
                IOException("Seeking is not supported by ${javaClass.simpleName}")
            )
        }
        nextOffset += size

        job?.let {
            if (it.isCompleted) {
                throw ErrnoException(
                    "onWrite",
                    OsConstants.EIO,
                    IOException("WebDAV server unexpectedly closed the connection")
                )
            }
        }

        if (pipe == null) {
            pipe = Pipe(min(size.toLong(), 4096)).also {
                sink = it.sink.buffer()

                job = scope.launch {
                    val res = client.putFile(file, it.source, contentType = file.contentType)
                    if (res.isSuccessful) {
                        val propRes = client.propFind(file.davPath)
                        if (propRes.isSuccessful) {
                            onSuccess(propRes.body!!)
                        } else {
                            propRes.error?.message.let { msg ->
                                Log.e(TAG, "propFind failed: ${msg}\")")
                                onFail()
                            }
                        }
                    } else {
                        res.error?.message.let { msg ->
                            Log.e(TAG, "Upload failed: ${msg}\")")
                            onFail()
                        }
                    }
                }
            }
        }

        sink!!.write(data, 0, size)
        return size
    }

    override fun onFsync() {
        // We can't do a proper fsync here, but we can flush the sink
        Log.d(TAG, "onFsync()")
        sink?.flush()
    }

    override fun onRelease() {
        Log.d(TAG, "onRelease()")
        sink?.close()
        runBlocking { join() }
        Log.d(TAG, "onRelease(): Done!")
    }

    suspend fun join() {
        job?.join()
    }
}
