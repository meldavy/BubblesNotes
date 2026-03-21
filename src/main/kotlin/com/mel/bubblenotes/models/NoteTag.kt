package com.mel.bubblenotes.models

import com.mel.bubblenotes.serializers.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Join table entity representing many-to-many relationship between notes and tags.
 * Each entry links a note to a tag belonging to the same user.
 */
@Serializable
data class NoteTag(
    val noteId: Long,
    val tagId: Long,
    val userId:
        @Serializable(with = UUIDSerializer::class)
        UUID,
)
