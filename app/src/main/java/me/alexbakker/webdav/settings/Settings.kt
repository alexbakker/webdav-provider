package me.alexbakker.webdav.settings

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class Settings(val accounts: MutableList<Account> = ArrayList()) {
    fun save(context: Context) {
        // openFileOutput raises an exception if the file already exists (only god knows why)
        // see: https://issuetracker.google.com/issues/148804720
        // TODO: check back in a while to see if this is still the case
        getFile(context).delete()

        getEncryptedFile(context).openFileOutput().use { stream ->
            val data = Json.encodeToString(this).encodeToByteArray()
            stream.write(data)
        }
    }

    companion object {
        private const val FILENAME = "settings"

        private fun getFile(context: Context): File {
            return File(context.filesDir, FILENAME)
        }

        private fun getEncryptedFile(context: Context): EncryptedFile {
            val key = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedFile.Builder(
                context,
                getFile(context),
                key,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
        }

        fun fileExists(context: Context): Boolean {
            return getFile(context).exists()
        }

        fun readFile(context: Context): Settings {
            getEncryptedFile(context).openFileInput().use { stream ->
                val data = stream.readBytes()
                return json.decodeFromString(data.decodeToString())
            }
        }
    }
}

fun MutableList<Account>.byUUID(uuid: UUID): Account {
    return this.single { v -> v.uuid == uuid }
}

fun MutableList<Account>.byUUIDOrNull(uuid: UUID): Account? {
    return this.singleOrNull { v -> v.uuid == uuid }
}
