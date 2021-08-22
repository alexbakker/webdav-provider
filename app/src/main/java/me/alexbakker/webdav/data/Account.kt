package me.alexbakker.webdav.data

import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import me.alexbakker.webdav.BuildConfig
import me.alexbakker.webdav.provider.WebDavClient
import me.alexbakker.webdav.provider.WebDavFile

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

    @ColumnInfo(name = "max_cache_file_size")
    var maxCacheFileSize: Long = 20
) {
    @Ignore
    private var _root: WebDavFile? = null

    var root: WebDavFile
        get() {
            if (_root == null) {
                _root = WebDavFile(rootPath, true)
            }

            return _root!!
        }
        set(value) {
            value.isRoot = true
            _root = value
        }

    private val authentication: Boolean
        get() {
            return username != null && password != null
        }

    val rootPath: String
        get() {
            return ensureTrailingSlash(baseUrl.encodedPath!!)
        }

    val rootUri: Uri
        get() {
            return DocumentsContract.buildRootUri(BuildConfig.PROVIDER_AUTHORITY, id.toString())
        }

    @Ignore
    private var _client: WebDavClient? = null

    private val baseUrl: Uri
        get() {
            return Uri.parse(url)
        }

    val client: WebDavClient
        get() {
            if (_client == null) {
                val url = ensureTrailingSlash(baseUrl.toString())
                _client = WebDavClient(
                    url,
                    if (authentication) Pair(username!!, password!!) else null,
                    noHttp2 = protocol != Protocol.AUTO
                )
            }

            return _client!!
        }

    fun resetState() {
        _client = null
        _root = null
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
