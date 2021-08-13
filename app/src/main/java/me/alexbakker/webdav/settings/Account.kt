package me.alexbakker.webdav.settings

import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.alexbakker.webdav.BuildConfig
import me.alexbakker.webdav.provider.WebDavClient
import me.alexbakker.webdav.provider.WebDavFile
import java.util.*

@Serializable
data class Account(
        @Serializable(with = UUIDSerializer::class)
        var uuid: UUID = UUID.randomUUID(),
        var name: String? = null,
        var url: String? = null,
        var verifyCerts: Boolean = true,
        var authentication: Boolean = true,
        var username: String? = null,
        var password: String? = null,
) {
    @Transient
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

    private val rootPath: String
        get() {
            return ensureTrailingSlash(baseUrl.encodedPath!!)
        }

    val rootUri: Uri
        get() {
            return DocumentsContract.buildRootUri(BuildConfig.PROVIDER_AUTHORITY, uuid.toString())
        }

    @Transient
    private var _client: WebDavClient? = null

    private val baseUrl: Uri
        get() {
            return Uri.parse(url)
        }

    val client: WebDavClient
        get() {
            if (_client == null) {
                val url = ensureTrailingSlash(baseUrl.toString())
                _client = WebDavClient(url, if (authentication) Pair(username!!, password!!) else null)
            }

            return _client!!
        }

    fun resetState() {
        _client = null
        _root = null
    }

    private fun ensureTrailingSlash(s: String): String {
        var res = s
        if (!s.endsWith("/")) {
            res += "/"
        }

        return res
    }
}

@Serializer(forClass = UUID::class)
object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}
