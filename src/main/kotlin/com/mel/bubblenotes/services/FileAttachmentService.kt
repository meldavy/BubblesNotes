package com.mel.bubblenotes.services

import com.mel.bubblenotes.baseStorageDir
import com.mel.bubblenotes.models.FileAttachment
import java.io.File
import java.util.UUID

/**
 * Interface for file attachment repository operations.
 * Allows mocking in unit tests.
 */
interface FileAttachmentDataSource {
    fun create(attachment: FileAttachment): Long

    fun findByUserId(userId: UUID): List<FileAttachment>

    fun findById(id: Long): FileAttachment?

    fun deleteById(id: Long): Int

    fun isAuthorizedForAttachment(
        attachmentId: Long,
        userId: UUID,
    ): Boolean
}

/**
 * Service for handling file upload operations.
 * Handles validation, encryption, storage, and database persistence.
 * Supports both images and general file attachments (PDF, DOC, TXT, ZIP, etc.).
 */
class FileAttachmentService(
    private val fileAttachmentRepository: FileAttachmentDataSource,
    private val encryptionService: com.mel.bubblenotes.services.EncryptionService,
    private val storageDir: String = baseStorageDir,
) {
    companion object {
        const val MAX_FILE_SIZE = 10_485_760L // 10MB
    }

    /**
     * Upload a file (standalone, independent of any note).
     * Supports images and general file attachments.
     * @param file The uploaded file
     * @param userId The ID of the user uploading the file
     * @param fileName The original file name
     * @param contentType The MIME type of the file
     * @return The storage path that can be referenced in note content
     */
    fun uploadFile(
        file: File,
        userId: UUID,
        fileName: String,
        contentType: String,
    ): String {
        // Validate content type using MIME type category matching
        if (!FileAttachment.isAllowedContentType(contentType)) {
            throw IllegalArgumentException(
                "Unsupported file type: $contentType. Allowed types: images, documents, text files, archives, code files",
            )
        }

        // Validate file size
        val fileSize = file.length()
        if (fileSize > MAX_FILE_SIZE) {
            throw IllegalArgumentException(
                "File size exceeds 10MB limit: ${fileSize / 1024 / 1024}MB",
            )
        }

        // Generate storage path
        val storagePath = generateStoragePath(userId, fileName)

        // Read file content and encrypt it
        val fileBytes = file.readBytes()
        val encryptedData = encryptionService.encrypt(fileBytes)

        // Create attachment record
        val attachment =
            FileAttachment(
                id = 0,
                userId = userId,
                fileName = fileName,
                contentType = contentType,
                fileSize = fileSize,
                storagePath = storagePath,
                createdAt = System.currentTimeMillis(),
            )

        // Save encrypted file to storage
        saveEncryptedFile(encryptedData, storagePath)

        // Persist to database
        fileAttachmentRepository.create(attachment)

        // Return the storage path for reference in note content
        return storagePath
    }

    /**
     * Generate a unique storage path for the encrypted file.
     * The path is relative to the base storage directory.
     */
    private fun generateStoragePath(
        userId: UUID,
        fileName: String,
    ): String {
        val uniqueId = UUID.randomUUID().toString()
        val extension = fileName.substringAfterLast(".", "")
        return "$userId/$uniqueId.$extension"
    }

    /**
     * Save encrypted file data to storage.
     * For now, uses file system. In production, this would use cloud storage.
     */
    private fun saveEncryptedFile(
        encryptedData: ByteArray,
        storagePath: String,
    ) {
        // Combine base storage directory with the relative storage path
        val fullPath = File(storageDir, storagePath)
        fullPath.parentFile?.mkdirs()
        fullPath.writeBytes(encryptedData)
    }

    /**
     * Load a file attachment by ID.
     */
    fun getFileAttachment(id: Long): FileAttachment? {
        return fileAttachmentRepository.findById(id)
    }

    /**
     * Get all files for a user.
     */
    fun getFilesForUser(userId: UUID): List<FileAttachment> {
        return fileAttachmentRepository.findByUserId(userId)
    }

    /**
     * Delete a file attachment.
     */
    fun deleteFileAttachment(id: Long) {
        val attachment = fileAttachmentRepository.findById(id)
        if (attachment != null) {
            // Delete file from storage (use base storage directory)
            val fullPath = File(storageDir, attachment.storagePath)
            if (fullPath.exists()) {
                fullPath.delete()
            }
            // Delete from database
            fileAttachmentRepository.deleteById(id)
        }
    }
}
