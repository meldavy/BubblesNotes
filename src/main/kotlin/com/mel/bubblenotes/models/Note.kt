package com.mel.bubblenotes.models

import com.mel.bubblenotes.serializers.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Note(
    val id: Long,
    val userId:
        @Serializable(with = UUIDSerializer::class)
        UUID,
    val title: String?,
    val content: String,
    val isPublished: Boolean,
    // Primary user-managed tags embedded directly on the note
    val tags: List<String> = emptyList(),
    val aiTitle: String? = null,
    val aiSummary: String? = null,
    val aiTags: List<String>? = null,
    val lastVersionId: Long? = null,
    // JSON string containing URL previews
    val previewData: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
