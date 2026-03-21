package com.mel.bubblenotes.models

import com.mel.bubblenotes.serializers.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class APIKey(
    val id: Long,
    val userId:
        @Serializable(with = UUIDSerializer::class)
        UUID,
    var tokenHash: String,
    var name: String,
    var lastUsedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
