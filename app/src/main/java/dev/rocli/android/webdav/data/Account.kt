package dev.rocli.android.webdav.data

import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.rocli.android.webdav.BuildConfig
import dev.rocli.android.webdav.extensions.ensureTrailingSlash
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.nio.file.Path
import java.nio.file.Paths

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
    var username: SecretString? = null,

    @ColumnInfo(name = "password")
    var password: SecretString? = null,

    @ColumnInfo(name = "client_cert")
    var clientCert: String? = null,

    @ColumnInfo(name = "max_cache_file_size")
    var maxCacheFileSize: Long = 20,

    @ColumnInfo(name = "act_as_local_storage", defaultValue = "false")
    var actAsLocalStorage: Boolean = false
) {
    val rootPath: Path
        get() {
            val path = baseUrl.encodedPath.ensureTrailingSlash()
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

    val baseUrl: HttpUrl
        get() {
            return url!!.ensureTrailingSlash().toHttpUrl()
        }

    val hasError: Boolean
        get() {
            return username?.error != null || password?.error != null
        }

    enum class Protocol {
        AUTO, HTTP1
    }
}

fun List<Account>.byId(id: Long): Account {
    return this.single { v -> v.id == id }
}
