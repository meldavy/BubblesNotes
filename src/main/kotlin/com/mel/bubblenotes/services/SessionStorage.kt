package com.mel.bubblenotes.services

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted session storage for authentication tokens.
 * Uses AES encryption with a centralized key to store and retrieve session data securely.
 */
class SessionStorage(private val encryptionKey: String) {

    private val algorithm = "AES"
    private val cipherMode = "AES/ECB/PKCS5Padding"

    /**
     * Stores a session token with the given key.
     * The token is encrypted before storage.
     */
    fun store(key: String, token: String): Boolean {
        return try {
            val encryptedToken = encrypt(token)
            // In production, use a proper database or cache (Redis, etc.)
            // For now, using an in-memory map as a placeholder
            sessions[key] = EncryptedSession(encryptedToken, System.currentTimeMillis())
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Retrieves and decrypts a session token by key.
     * Returns null if the token doesn't exist or decryption fails.
     */
    fun retrieve(key: String): String? {
        return try {
            val session = sessions[key] ?: return null
            // Check if session has expired (24 hours default)
            if (System.currentTimeMillis() - session.createdAt > 24 * 60 * 60 * 1000) {
                sessions.remove(key)
                return null
            }
            decrypt(session.encryptedToken)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Removes a session token by key.
     */
    fun remove(key: String): Boolean {
        return sessions.remove(key) != null
    }

    /**
     * Checks if a session exists for the given key.
     */
    fun exists(key: String): Boolean {
        return sessions.containsKey(key)
    }

    /**
     * Clears all expired sessions.
     */
    fun cleanup() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { it.value.createdAt + 24 * 60 * 60 * 1000 < now }
    }

    // In-memory storage (replace with database/cache in production)
    private val sessions = mutableMapOf<String, EncryptedSession>()

    private data class EncryptedSession(val encryptedToken: String, val createdAt: Long)

    /**
     * Encrypts a string using AES encryption.
     */
    private fun encrypt(data: String): String {
        val cipher = Cipher.getInstance(cipherMode)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val encryptedBytes = cipher.doFinal(data.toByteArray())
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }

    /**
     * Decrypts an encrypted string using AES encryption.
     */
    private fun decrypt(encryptedData: String): String {
        val cipher = Cipher.getInstance(cipherMode)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey())
        val decodedBytes = Base64.getDecoder().decode(encryptedData)
        val decryptedBytes = cipher.doFinal(decodedBytes)
        return String(decryptedBytes)
    }

    /**
     * Generates a SecretKey from the encryption key string.
     */
    private fun getSecretKey(): SecretKeySpec {
        // Use SHA-256 to derive a 256-bit key from the provided string
        val messageDigest = java.security.MessageDigest.getInstance("SHA-256")
        val keyBytes = messageDigest.digest(encryptionKey.toByteArray())
        return SecretKeySpec(keyBytes, algorithm)
    }
}
