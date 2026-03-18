package com.mel.bubblenotes.services

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.SecureRandom
import java.util.Base64

class EncryptionService(private val encryptionKey: String = System.getenv("ENCRYPTION_KEY") ?: "default-key-change-in-production") {
    
    fun encrypt(data: ByteArray): ByteArray {
        val secretKey = generateSecretKey()
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(data)
        
        // Return IV + encrypted data
        return iv + encrypted
    }
    
    fun decrypt(encryptedData: ByteArray): ByteArray {
        val iv = encryptedData.copyOfRange(0, 12)
        val actualEncrypted = encryptedData.copyOfRange(12, encryptedData.size)
        
        val secretKey = generateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(actualEncrypted)
    }
    
    fun encryptString(data: String): String {
        return Base64.getEncoder().encodeToString(encrypt(data.toByteArray()))
    }
    
    fun decryptString(encryptedData: String): String {
        return String(decrypt(Base64.getDecoder().decode(encryptedData)))
    }
    
    private fun generateSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256, SecureRandom(encryptionKey.toByteArray()))
        return keyGenerator.generateKey()
    }
}
