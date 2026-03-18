package com.mel.bubblenotes.services

import java.security.SecureRandom
import java.util.Base64

class ApiKeyService {
    
    fun generateApiKey(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }
    
    fun validateApiKey(apiKey: String): Boolean {
        // API key should be exactly 43 characters (Base64 encoded 32 bytes)
        if (apiKey.length != 43) return false
        
        // Check if all characters are valid Base64
        return apiKey.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=" }
    }
    
    fun hashApiKey(apiKey: String): String {
        val bytes = apiKey.toByteArray()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashed = digest.digest(bytes)
        return Base64.getEncoder().encodeToString(hashed)
    }
}
