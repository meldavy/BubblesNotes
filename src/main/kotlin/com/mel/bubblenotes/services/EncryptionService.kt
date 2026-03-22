package com.mel.bubblenotes.services

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EncryptionService(private val encryptionKey: String) {
    // Derive a stable AES key from the configured encryption key using PBKDF2
    private fun deriveSecretKey(saltBytes: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec =
            PBEKeySpec(
                encryptionKey.toCharArray(),
                saltBytes,
                65536,
                256,
            ) // Salt: per-user or fixed for global data // iterations - high count for security // key length in bits
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    /**
     * Encrypts data with optional user encryption salt.
     * If encryptionSalt is provided, derives a per-user encryption key using that salt.
     * If encryptionSalt is null, uses a fixed salt for global/shared data.
     */
    fun encrypt(
        data: ByteArray,
        encryptionSalt: String? = null,
    ): ByteArray {
        // Use user's encryption salt if provided, otherwise use fixed salt for global data
        val saltBytes =
            if (encryptionSalt != null) {
                Base64.getDecoder().decode(encryptionSalt) // User-specific random salt from DB
            } else {
                "bubblenotes-global-salt".toByteArray() // Fixed salt for shared data
            }

        val secretKey = deriveSecretKey(saltBytes)
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) } // Random IV per encryption

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(data)

        // Return IV + encrypted data (IV is needed for decryption)
        return iv + encrypted
    }

    /**
     * Decrypts data with optional user encryption salt.
     * Must use the same encryptionSalt that was used during encryption.
     */
    fun decrypt(
        encryptedData: ByteArray,
        encryptionSalt: String? = null,
    ): ByteArray {
        val iv = encryptedData.copyOfRange(0, 12)
        val actualEncrypted = encryptedData.copyOfRange(12, encryptedData.size)

        // Use user's encryption salt if provided, otherwise use fixed salt for global data
        val saltBytes =
            if (encryptionSalt != null) {
                Base64.getDecoder().decode(encryptionSalt) // User-specific random salt from DB
            } else {
                "bubblenotes-global-salt".toByteArray() // Fixed salt for shared data
            }

        val secretKey = deriveSecretKey(saltBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(actualEncrypted)
    }

    /**
     * Encrypts a string with optional user encryption salt.
     */
    fun encryptString(
        data: String,
        encryptionSalt: String? = null,
    ): String {
        return Base64.getEncoder().encodeToString(encrypt(data.toByteArray(), encryptionSalt))
    }

    /**
     * Decrypts a string with optional user encryption salt.
     */
    fun decryptString(
        encryptedData: String,
        encryptionSalt: String? = null,
    ): String {
        return String(decrypt(Base64.getDecoder().decode(encryptedData), encryptionSalt))
    }
}
