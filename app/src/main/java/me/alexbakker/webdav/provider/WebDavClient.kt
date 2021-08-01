package me.alexbakker.webdav.provider

import com.thegrizzlylabs.sardineandroid.model.Prop
import com.thegrizzlylabs.sardineandroid.model.Property
import com.thegrizzlylabs.sardineandroid.model.Resourcetype
import com.thegrizzlylabs.sardineandroid.util.EntityWithAnyElementConverter
import me.alexbakker.webdav.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import org.simpleframework.xml.Serializer
import org.simpleframework.xml.convert.Registry
import org.simpleframework.xml.convert.RegistryStrategy
import org.simpleframework.xml.core.Persister
import org.simpleframework.xml.strategy.Strategy
import org.simpleframework.xml.stream.Format
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.io.IOException
import java.io.InputStream

class WebDavClient(private val url: String, private val creds: Pair<String, String>? = null) {
    private var _api: WebDavService? = null

    private val serializer: Serializer
        get() {
            // source: https://git.io/Jkf9B
            val format = Format("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            val registry = Registry()
            val strategy: Strategy = RegistryStrategy(registry)
            val serializer: Serializer = Persister(strategy, format)
            registry.bind(
                Prop::class.java,
                EntityWithAnyElementConverter(serializer, Prop::class.java)
            )
            registry.bind(
                Resourcetype::class.java,
                EntityWithAnyElementConverter(serializer, Resourcetype::class.java)
            )
            registry.bind(Property::class.java, Property.PropertyConverter::class.java)
            return serializer
        }

    private val api: WebDavService
        get() {
            if (_api == null) {
                val builder = OkHttpClient.Builder()
                /*if (BuildConfig.DEBUG) {
                    val logging = HttpLoggingInterceptor()
                    logging.level = HttpLoggingInterceptor.Level.HEADERS
                    builder.addInterceptor(logging)
                }*/
                if (creds != null) {
                    val creds = Credentials.basic(creds.first, creds.second)
                    builder.addInterceptor(AuthInterceptor(creds))
                }

                val converter = SimpleXmlConverterFactory.create(serializer)
                _api = Retrofit.Builder()
                    .baseUrl(url)
                    .client(builder.build())
                    .addConverterFactory(converter)
                    .build()
                    .create(WebDavService::class.java)
            }

            return _api!!
        }

    suspend fun get(path: String, offset: Long = 0): Result<InputStream> {
        val res = execRequest { api.get(path, if (offset == 0L) null else "bytes=$offset-") }
        if (!res.isSuccessful) {
            return Result(error = res.error)
        }

        val contentLength = res.body?.contentLength()
        return Result(res.body?.byteStream(), contentLength = if (contentLength == -1L) null else contentLength)
    }

    suspend fun put(path: String, inStream: InputStream? = null, contentType: String? = null): Result<Unit> {
        return execRequest {
            if (inStream != null) {
                val body = StreamRequestBody((contentType ?: "application/octet-stream").toMediaType(), inStream)
                api.put(path, body)
            } else {
                api.putEmpty(path)
            }
        }
    }

    suspend fun delete(path: String): Result<Unit> {
        return execRequest { api.delete(path) }
    }

    suspend fun propFind(path: String): Result<WebDavFile> {
        val res = execRequest { api.propFind(path) }
        if (!res.isSuccessful) {
            return Result(error = res.error)
        }

        val root = WebDavFile(path, true)
        for (desc in res.body!!.response) {
            val file = WebDavFile(desc)

            var parent = root
            for (part in file.path.removePrefix(path).split("/")) {
                if (part.isEmpty()) {
                    continue
                }

                var next: WebDavFile? = null
                for (child in parent.children) {
                    if (child.name == part) {
                        next = child
                        break
                    }
                }

                if (next == null) {
                    file.parent = parent
                    parent.children.add(file)
                    break
                }

                parent = next
            }
        }

        return Result(root)
    }

    private suspend fun <T> execRequest(exec: suspend () -> Response<T>): Result<T> {
        val res: Response<T>
        try {
            res = exec()
            if (!res.isSuccessful) {
                throw IOException("Status code: ${res.code()}")
            }
        } catch (e: IOException) {
            return Result(error = e)
        }

        return Result(res.body())
    }

    data class Result<T>(
        val body: T? = null,
        val contentLength: Long? = null,
        val error: Exception? = null
    ) {
        val isSuccessful: Boolean
            get() {
                return error == null
            }
    }

    private class AuthInterceptor(val auth: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val req = chain.request().newBuilder()
                .header("Authorization", auth)
                .build()
            return chain.proceed(req)
        }
    }

    private class StreamRequestBody(
        private var contentType: MediaType,
        private var inputStream: InputStream
    ) : RequestBody() {
        override fun contentType(): MediaType? {
            return contentType
        }

        override fun isOneShot(): Boolean {
            return true
        }

        @Throws(IOException::class)
        override fun writeTo(sink: BufferedSink) {
            sink.outputStream().use { outStream ->
                inputStream.copyTo(outStream)
            }
        }
    }
}
