package com.mel.bubblenotes.services

import com.mel.bubblenotes.models.FileAttachment
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for FileAttachmentService.
 *
 * These tests focus on:
 * - Service method that delegates to repository (getFileAttachment, deleteFileAttachment)
 * - Content type validation for various file types
 *
 * Note: The uploadFile method requires file system operations and encryption,
 * which are tested in integration tests with a real database and storage.
 */
class FileAttachmentServiceTest {
    companion object {
        private val TEST_USER_ID = UUID.randomUUID()
        private const val TEST_ENCRYPTION_KEY = "test-encryption-key-for-testing-only"
    }

    /**
     * In-memory implementation of FileAttachmentDataSource for unit tests.
     */
    private class InMemoryFileAttachmentDataSource : FileAttachmentDataSource {
        private val attachments = mutableMapOf<Long, FileAttachment>()
        private var nextId = 1L

        override fun create(attachment: FileAttachment): Long {
            val id = nextId++
            attachments[id] = attachment.copy(id = id)
            return id
        }

        override fun findByUserId(userId: UUID): List<FileAttachment> {
            return attachments.values.filter { it.userId == userId }
        }

        override fun findById(id: Long): FileAttachment? {
            return attachments[id]
        }

        override fun deleteById(id: Long): Int {
            return if (attachments.remove(id) != null) 1 else 0
        }

        override fun isAuthorizedForAttachment(
            attachmentId: Long,
            userId: UUID,
        ): Boolean {
            val attachment = attachments[attachmentId]
            return attachment?.userId == userId
        }

        fun clear() {
            attachments.clear()
            nextId = 1L
        }
    }

    @Test
    fun `getFileAttachment should return attachment from repository`() {
        // Arrange
        val attachmentId = 1L
        val expectedAttachment =
            FileAttachment(
                id = attachmentId,
                userId = TEST_USER_ID,
                fileName = "test.png",
                contentType = "image/png",
                fileSize = 1024L,
                storagePath = "files/test/test.png",
                createdAt = System.currentTimeMillis(),
            )

        val repository = InMemoryFileAttachmentDataSource()
        // Pre-populate repository
        val createdId = repository.create(expectedAttachment)

        val encryptionService = EncryptionService(TEST_ENCRYPTION_KEY)
        val service = FileAttachmentService(repository, encryptionService)

        // Act
        val result = service.getFileAttachment(createdId)

        // Assert
        assertNotNull(result)
        assertEquals(createdId, result.id)
        assertEquals("test.png", result.fileName)
    }

    @Test
    fun `getFileAttachment should return null for non-existent attachment`() {
        // Arrange
        val attachmentId = 999L

        val repository = InMemoryFileAttachmentDataSource()
        val encryptionService = EncryptionService(TEST_ENCRYPTION_KEY)
        val service = FileAttachmentService(repository, encryptionService)

        // Act
        val result = service.getFileAttachment(attachmentId)

        // Assert
        assertEquals(null, result)
    }

    @Test
    fun `deleteFileAttachment should handle non-existent attachment`() {
        // Arrange
        val attachmentId = 999L

        val repository = InMemoryFileAttachmentDataSource()
        val encryptionService = EncryptionService(TEST_ENCRYPTION_KEY)
        val service = FileAttachmentService(repository, encryptionService)

        // Act & Assert - should not throw
        service.deleteFileAttachment(attachmentId)

        // Verify repository was queried
        val result = repository.findById(attachmentId)
        assertEquals(null, result)
    }

    @Test
    fun `deleteFileAttachment should delete existing attachment`() {
        // Arrange
        val attachment =
            FileAttachment(
                id = 0,
                userId = TEST_USER_ID,
                fileName = "test.png",
                contentType = "image/png",
                fileSize = 1024L,
                storagePath = "files/test/test.png",
                createdAt = System.currentTimeMillis(),
            )

        val repository = InMemoryFileAttachmentDataSource()
        val createdId = repository.create(attachment)
        val encryptionService = EncryptionService(TEST_ENCRYPTION_KEY)
        val service = FileAttachmentService(repository, encryptionService)

        // Act
        service.deleteFileAttachment(createdId)

        // Assert
        val result = repository.findById(createdId)
        assertEquals(null, result, "Attachment should be deleted")
    }

    @Test
    fun `uploadFile should accept image content types`() {
        // Arrange
        val imageTypes = listOf("image/png", "image/jpeg", "image/gif", "image/webp")

        // Act & Assert - should not throw for any image type
        imageTypes.forEach { contentType ->
            assertTrue(FileAttachment.isAllowedContentType(contentType), "$contentType should be allowed")
        }
    }

    @Test
    fun `uploadFile should accept document content types`() {
        // Arrange
        val documentTypes =
            listOf(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            )

        // Act & Assert - should not throw for any document type
        documentTypes.forEach { contentType ->
            assertTrue(FileAttachment.isAllowedContentType(contentType), "$contentType should be allowed")
        }
    }

    @Test
    fun `uploadFile should accept text content types`() {
        // Arrange
        val textTypes = listOf("text/plain", "text/csv", "text/html")

        // Act & Assert - should not throw for any text type
        textTypes.forEach { contentType ->
            assertTrue(FileAttachment.isAllowedContentType(contentType), "$contentType should be allowed")
        }
    }

    @Test
    fun `uploadFile should accept archive content types`() {
        // Arrange
        val archiveTypes = listOf("application/zip", "application/gzip")

        // Act & Assert - should not throw for any archive type
        archiveTypes.forEach { contentType ->
            assertTrue(FileAttachment.isAllowedContentType(contentType), "$contentType should be allowed")
        }
    }

    @Test
    fun `uploadFile should reject executable content types`() {
        // Arrange
        val blockedTypes =
            listOf(
                "application/x-executable",
                "application/x-msdownload",
                "application/java-archive",
            )

        // Act & Assert - should reject all blocked types
        blockedTypes.forEach { contentType ->
            assertTrue(!FileAttachment.isAllowedContentType(contentType), "$contentType should be blocked")
        }
    }

    @Test
    fun `FileAttachment isImage property should work correctly`() {
        // Arrange & Act
        val imageAttachment =
            FileAttachment(
                id = 0,
                userId = TEST_USER_ID,
                fileName = "test.png",
                contentType = "image/png",
                fileSize = 1024L,
                storagePath = "files/test/test.png",
                createdAt = System.currentTimeMillis(),
            )

        val documentAttachment =
            FileAttachment(
                id = 0,
                userId = TEST_USER_ID,
                fileName = "test.pdf",
                contentType = "application/pdf",
                fileSize = 1024L,
                storagePath = "files/test/test.pdf",
                createdAt = System.currentTimeMillis(),
            )

        // Assert
        assertTrue(imageAttachment.isImage, "Image attachment should have isImage = true")
        assertTrue(!documentAttachment.isImage, "Document attachment should have isImage = false")
    }

    @Test
    fun `markdown syntax should be correct for images and files`() {
        // Arrange
        val imageUrl = "/api/v1/attachments/download?path=files/test/image.png"
        val fileUrl = "/api/v1/attachments/download?path=files/test/document.pdf"

        // Act
        val imageMarkdown = FileAttachment.getMarkdownSyntax("image.png", imageUrl, true)
        val fileMarkdown = FileAttachment.getMarkdownSyntax("document.pdf", fileUrl, false)

        // Assert
        assertEquals("![image.png]($imageUrl)", imageMarkdown)
        assertEquals("[document.pdf]($fileUrl)", fileMarkdown)
    }
}
