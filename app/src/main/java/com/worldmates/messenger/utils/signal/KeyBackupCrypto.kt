package com.worldmates.messenger.utils.signal

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Client-side AES-256-GCM encryption for E2EE key backup.
 *
 * Scheme:
 *   salt       = 32 random bytes  (new each backup)
 *   iv         = 12 random bytes  (AES-GCM nonce, new each backup)
 *   key        = PBKDF2WithHmacSHA256(password, salt, 100 000 itr, 256 bit)
 *   ciphertext = AES/GCM/NoPadding encrypt(plaintext, key, iv)
 *                — includes 128-bit GCM auth tag appended by the JCE provider
 *
 * The server stores only { version, salt, iv, ciphertext } and cannot decrypt.
 */
object KeyBackupCrypto {

    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS   = 256
    private const val SALT_BYTES        = 32
    private const val IV_BYTES          = 12
    private const val GCM_TAG_BITS      = 128

    data class EncryptedBlob(
        @SerializedName("version")    val version:   Int    = 1,
        @SerializedName("salt")       val salt:      String,
        @SerializedName("iv")         val iv:        String,
        @SerializedName("ciphertext") val ciphertext: String,
    )

    /** Encrypts [plaintext] with [password]. Returns an [EncryptedBlob]. */
    fun encrypt(password: String, plaintext: String): EncryptedBlob {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv   = ByteArray(IV_BYTES).also  { SecureRandom().nextBytes(it) }
        val key  = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedBlob(
            salt       = Base64.encodeToString(salt,       Base64.NO_WRAP),
            iv         = Base64.encodeToString(iv,         Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
        )
    }

    /**
     * Decrypts [blob] with [password].
     * @throws javax.crypto.AEADBadTagException if password is wrong or blob is corrupt.
     */
    fun decrypt(password: String, blob: EncryptedBlob): String {
        val salt       = Base64.decode(blob.salt,       Base64.NO_WRAP)
        val iv         = Base64.decode(blob.iv,         Base64.NO_WRAP)
        val ciphertext = Base64.decode(blob.ciphertext, Base64.NO_WRAP)
        val key        = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec    = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return factory.generateSecret(spec).encoded
    }

    // Convenience: serialise/deserialise blob via Gson
    private val gson = Gson()
    fun blobToJson(blob: EncryptedBlob): String = gson.toJson(blob)
    fun blobFromJson(json: String): EncryptedBlob = gson.fromJson(json, EncryptedBlob::class.java)
}
