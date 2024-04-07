package me.alexbakker.webdav.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.TypeConverter
import java.security.KeyStore
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
class SecretStringConverter {
    @TypeConverter
    fun decrypt(encrypted: String?): SecretString? {
        if (encrypted == null) {
            return null
        }

        val blob = Base64.Default.decode(encrypted)
        if (blob.size < NONCE_SIZE + TAG_SIZE) {
            return SecretString(error = IllegalArgumentException("String not encrypted: Unexpected length"))
        }
        val nonce = blob.sliceArray(0..<NONCE_SIZE)
        val encryptedBytes = blob.sliceArray(NONCE_SIZE..<blob.size)

        val key = getKey()
        val spec = GCMParameterSpec(TAG_SIZE * 8, nonce)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val decryptedBytes: ByteArray
        try {
            decryptedBytes = cipher.doFinal(encryptedBytes)
        } catch (e: Exception) {
            when (e) {
                is BadPaddingException, is IllegalBlockSizeException -> {
                    return SecretString(error = e)
                }
                else -> throw e
            }
        }

        return SecretString(decryptedBytes.decodeToString())
    }

    @TypeConverter
    fun encrypt(decrypted: SecretString?): String? {
        if (decrypted?.value == null) {
            return null
        }

        val key = getKey()
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val encryptedBytes = cipher.doFinal(decrypted.value.encodeToByteArray())
        return Base64.Default.encode(cipher.iv + encryptedBytes)
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEY_STORE)
        keyStore.load(null)

        return if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.getKey(KEY_ALIAS, null) as SecretKey
        } else {
            generateKey()
        }
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEY_STORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(KEY_SIZE * 8)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val CIPHER = "AES/GCM/NoPadding"
        const val KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "webdav_credentials_key"
        const val KEY_SIZE = 32
        const val TAG_SIZE = 16
        const val NONCE_SIZE = 12
    }
}
