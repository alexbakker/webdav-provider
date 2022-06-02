package me.alexbakker.webdav.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.security.KeyChain
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import me.alexbakker.webdav.BuildConfig
import me.alexbakker.webdav.provider.WebDavClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.nio.file.Path
import java.nio.file.Paths
import java.security.PrivateKey
import java.security.cert.X509Certificate

@Entity(tableName = "account")
data class Account(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    @ColumnInfo(name = "name")
    var name: String? = null,

    @ColumnInfo(name = "url")
    var url: String? = null,

    @ColumnInfo(name = "protocol", defaultValue = "AUTO")
    var protocol: Protocol = Protocol.AUTO,

    @ColumnInfo(name = "verify_certs")
    var verifyCerts: Boolean = true,

    @ColumnInfo(name = "username")
    var username: String? = null,

    @ColumnInfo(name = "password")
    var password: String? = null,

    @ColumnInfo(name = "client_cert")
    var clientCert: String? = null,

    @ColumnInfo(name = "max_cache_file_size")
    var maxCacheFileSize: Long = 20,

    @ColumnInfo(name = "act_as_local_storage", defaultValue = "false")
    var actAsLocalStorage: Boolean = false
) {
    private val authentication: Boolean
        get() {
            return username != null && password != null
        }

    private val mutualAuth: Boolean
        get() {
            return !clientCert.isNullOrBlank()
        }

    val rootPath: Path
        get() {
            val path = ensureTrailingSlash(baseUrl.encodedPath)
            return Paths.get(path)
        }

    val rootId: String
        get() {
            return id.toString()
        }

    val rootUri: Uri
        get() {
            return DocumentsContract.buildRootUri(BuildConfig.PROVIDER_AUTHORITY, rootId)
        }

    @Ignore
    private var _client: WebDavClient? = null

    private val baseUrl: HttpUrl
        get() {
            return ensureTrailingSlash(url!!).toHttpUrl()
        }

    fun getClient(context: Context): WebDavClient {
        _client?.let{ return it }

        // TODO: Notify the user if the client certificate was removed without updating the WebDAV account
        var mutualCreds: Triple<String, PrivateKey, Array<X509Certificate>>? = null
        if (mutualAuth) {
            val privateKey = KeyChain.getPrivateKey(context, clientCert!!)
            val certChain = KeyChain.getCertificateChain(context, clientCert!!)
            if (privateKey != null && certChain != null) {
                mutualCreds = Triple(
                    clientCert!!,
                    privateKey,
                    certChain
                )
            }
        }

        val creds = if (authentication) Pair(username!!, password!!) else null
        WebDavClient(baseUrl, creds, mutualCreds, verifyCerts, noHttp2 = protocol != Protocol.AUTO).let{
            _client = it
            return it
        }
    }

    fun resetState() {
        _client = null
    }

    private fun ensureTrailingSlash(s: String): String {
        return if (!s.endsWith("/")) {
            "$s/"
        } else {
            s
        }
    }

    enum class Protocol {
        AUTO, HTTP1
    }
}

fun List<Account>.byId(id: Long): Account {
    return this.single { v -> v.id == id }
}
