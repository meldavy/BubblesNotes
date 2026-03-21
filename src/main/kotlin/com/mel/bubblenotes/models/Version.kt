package com.mel.bubblenotes.models

import com.mel.bubblenotes.serializers.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Version(
    val id: Long,
    val noteId: Long,
    val userId:
        @Serializable(with = UUIDSerializer::class)
        UUID,
    val title: String?,
    val content: String,
    val isPublished: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
)
