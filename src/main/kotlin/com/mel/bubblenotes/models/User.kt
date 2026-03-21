package com.mel.bubblenotes.models

import com.mel.bubblenotes.serializers.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class User(
    val id:
        @Serializable(with = UUIDSerializer::class)
        UUID,
    val email: String,
    val name: String,
    val givenName: String? = null,
    val familyName: String? = null,
    val pictureUrl: String? = null,
    val oauthToken: String,
    val encryptionSalt: String,
    val apiKey: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
