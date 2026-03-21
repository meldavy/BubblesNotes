package com.mel.bubblenotes.models

import com.mel.bubblenotes.serializers.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Tag(
    val id: Long,
    val userId:
        @Serializable(with = UUIDSerializer::class)
        UUID,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)
