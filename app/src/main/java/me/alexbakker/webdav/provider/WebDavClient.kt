package me.alexbakker.webdav.provider

import android.annotation.SuppressLint
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
import java.net.Socket
import java.nio.file.Path
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager


class WebDavClient(
    private val url: HttpUrl,
    private val creds: Pair<String, String>? = null,
    private val mutualCreds: Triple<String, PrivateKey, Array<X509Certificate>>? = null,
    private val verify: Boolean = true,
    private val noHttp2: Boolean = false
) {
    private val api: WebDavService = buildApiService(url, creds)

    data class Result<T>(
        val body: T? = null,
        val headers: Headers? = null,
        val contentLength: Long? = null,
        val error: Exception? = null
    ) {
        val isSuccessful: Boolean
            get() {
                return error == null
            }
    }

    class Error(message: String) : Exception(message)

    suspend fun get(path: String, offset: Long = 0): Result<InputStream> {
        val res = execRequest { api.get(path, if (offset == 0L) null else "bytes=$offset-") }
        if (!res.isSuccessful) {
            return Result(error = res.error)
        }

        val contentLength = res.body?.contentLength()
        return Result(
            res.body?.byteStream(),
            contentLength = if (contentLength == -1L) null else contentLength
        )
    }

    suspend fun putDir(path: String): Result<Unit> {
        return execRequest { api.putDir(path) }
    }

    suspend fun putFile(
        path: String,
        inStream: InputStream,
        contentType: String? = null,
        contentLength: Long = -1L
    ): Result<Unit> {
        return execRequest {
            val body = OneShotRequestBody(
                (contentType ?: "application/octet-stream").toMediaType(),
                inStream,
                contentLength = contentLength
            )
            api.putFile(path, body)
        }
    }

    suspend fun delete(path: String): Result<Unit> {
        return execRequest { api.delete(path) }
    }

    suspend fun options(path: String): Result<WebDavOptions> {
        val res = execRequest { api.options(path) }
        if (!res.isSuccessful) {
            return Result(error = res.error)
        }

        val allow = res.headers!!["Allow"]
            ?: return Result(error = Error("Header 'Allow' not present"))

        val methods = allow.split(",").map { m -> m.trim() }
        return Result(WebDavOptions(methods))
    }

    suspend fun propFindFile(path: Path): Result<WebDavFile> {
        return propFind(path, depth = 0)
    }

    suspend fun propFind(path: Path, depth: Int = 1): Result<WebDavFile> {
        val res = execRequest { api.propFind(path.toString(), depth = depth) }
        if (!res.isSuccessful) {
            return Result(error = res.error)
        }

        var root: WebDavFile? = null
        for (desc in res.body!!.response) {
            val file = WebDavFile(desc)
            if (file.path == path) {
                root = file
                break
            }
        }
        if (root == null) {
            return Result(error = Error("Root not found in PROPFIND response"))
        }

        for (desc in res.body.response) {
            val file = WebDavFile(desc)
            if (file.path != path) {
                file.parent = root
                root.children.add(file)
            }
        }

        return Result(root)
    }

    suspend fun move(path: Path, newPath: Path): Result<Unit> {
        val dest = url.newBuilder().encodedPath(newPath.toString()).build()
        return execRequest { api.move(path.toString(), dest.toString()) }
    }

    private suspend fun <T> execRequest(exec: suspend () -> Response<T>): Result<T> {
        var res: Response<T>? = null
        try {
            res = exec()
            if (!res.isSuccessful) {
                throw IOException("Status code: ${res.code()}")
            }
        } catch (e: IOException) {
            return Result(headers = res?.headers(), error = e)
        }

        return Result(body = res.body(), headers = res.headers())
    }

    private class AuthInterceptor(val auth: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val req = chain.request().newBuilder()
                .header("Authorization", auth)
                .build()
            return chain.proceed(req)
        }
    }

    private class OneShotRequestBody(
        private var contentType: MediaType,
        private var inputStream: InputStream,
        private var contentLength: Long = -1L,
    ) : RequestBody() {
        private var exhausted: Boolean = false

        override fun contentType() = contentType

        override fun contentLength(): Long = contentLength

        override fun isOneShot() = true

        @Throws(IOException::class)
        override fun writeTo(sink: BufferedSink) {
            // Believe it or not, certain developer environment configurations can cause
            // writeTo to be called twice. This happens when running the app with a debugger
            // attached and Database Inspector enabled, for instance. As a result, a 0-byte
            // file would appear on the server after a WebDAV upload action.
            if (exhausted) {
                throw RuntimeException("writeTo was called twice on a OneShotRequestBody")
            }

            exhausted = true
            sink.outputStream().use {
                inputStream.copyTo(it)
            }
        }
    }

    private fun buildApiService(url: HttpUrl, creds: Pair<String, String>?): WebDavService {
        val builder = OkHttpClient.Builder().apply {
            if (mutualCreds != null || !verify) {
                useCustomTLS(mutualCreds != null, verify)
            }
        }
        if (noHttp2) {
            builder.protocols(listOf(Protocol.HTTP_1_1))
        }
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor()
            logging.level = HttpLoggingInterceptor.Level.BASIC
            builder.addInterceptor(logging)
        }
        if (creds != null) {
            val auth = Credentials.basic(creds.first, creds.second)
            builder.addInterceptor(AuthInterceptor(auth))
        }

        val serializer = buildSerializer()
        val converter = SimpleXmlConverterFactory.create(serializer)
        return Retrofit.Builder()
            .baseUrl(url)
            .client(builder.build())
            .addConverterFactory(converter)
            .build()
            .create(WebDavService::class.java)
    }

    private fun OkHttpClient.Builder.useCustomTLS(mutual: Boolean, verify: Boolean): OkHttpClient.Builder {
        val sslContext = SSLContext.getInstance("TLS")
        val keyManager = if (mutual) {
            arrayOf<KeyManager>(object : X509KeyManager {
                override fun getClientAliases(
                    keyType: String?,
                    issuers: Array<Principal>
                ): Array<String> {
                    return arrayOf(mutualCreds!!.first)
                }

                override fun chooseClientAlias(
                    keyType: Array<out String>?,
                    issuers: Array<out Principal>?,
                    socket: Socket?
                ): String {
                    return mutualCreds!!.first
                }

                override fun getServerAliases(
                    keyType: String?,
                    issuers: Array<Principal>
                ): Array<String> {
                    return arrayOf()
                }

                override fun chooseServerAlias(
                    keyType: String?,
                    issuers: Array<Principal>,
                    socket: Socket
                ): String {
                    return ""
                }

                override fun getPrivateKey(alias: String?): PrivateKey {
                    return mutualCreds!!.second
                }

                override fun getCertificateChain(alias: String?): Array<X509Certificate> {
                    return mutualCreds!!.third
                }
            })
        } else {
            null
        }
        val trustManager: Array<TrustManager> = if (verify) {
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(null as KeyStore?)
            trustManagerFactory.trustManagers
        } else {
            hostnameVerifier { _, _ -> true }
            arrayOf(@SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
            })
        }
        sslContext.init(keyManager, trustManager, SecureRandom())
        sslSocketFactory(sslContext.socketFactory, trustManager.first() as X509TrustManager)
        return this
    }

    private fun buildSerializer(): Serializer {
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
}
