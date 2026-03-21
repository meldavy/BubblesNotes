package com.mel.bubblenotes.models

import com.mel.bubblenotes.serializers.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Attachment(
    val id: Long,
    val noteId: Long,
    val userId:
        @Serializable(with = UUIDSerializer::class)
        UUID,
    val fileName: String,
    val contentType: String,
    val fileSize: Long,
    val storagePath: String,
    val encryptedData: ByteArray? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
