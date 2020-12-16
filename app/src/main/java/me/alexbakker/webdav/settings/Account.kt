package me.alexbakker.webdav.settings

import android.net.Uri
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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
    var root = WebDavFile("/", true)

    @Transient
    private var _client: WebDavClient? = null

    val baseUrl: Uri
        get() {
            return Uri.parse(url)
        }

    val client: WebDavClient
        get() {
            if (_client == null) {
                _client = WebDavClient(baseUrl.toString(), if (authentication) Pair(username!!, password!!) else null)
            }

            return _client!!
        }

    fun buildURL(file: WebDavFile): String {
        var path: String = file.path
        if (path.startsWith("/")) {
            path = path.drop(1)
        }

        return Uri.parse(url)
            .buildUpon()
            .appendPath(path)
            .build()
            .toString()
    }

    fun resetClient() {
        _client = null
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
