package com.mel.bubblenotes.repositories

import com.mel.bubblenotes.models.FileAttachment
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileAttachmentRepositoryTest {
    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: FileAttachmentRepository

    fun setup() {
        // Setup H2 in-memory database (using H2-native syntax, no PostgreSQL mode needed)
        val hikariConfig =
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
                username = "sa"
                password = ""
                maximumPoolSize = 10
                minimumIdle = 2
            }
        dataSource = HikariDataSource(hikariConfig)

        // Run Flyway migrations
        try {
            val flyway =
                Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
            flyway.migrate()

            // Disable referential integrity for testing
            dataSource.connection.createStatement().use { stmt ->
                stmt.execute("SET REFERENTIAL_INTEGRITY FALSE")
            }
        } catch (e: Exception) {
            dataSource.close()
            throw e
        }

        repository = FileAttachmentRepository(dataSource)
    }

    fun teardown() {
        dataSource.close()
    }

    @Test
    fun testCreateAndFindAttachment() {
        setup()
        try {
            val userId = UUID.randomUUID()
            val now = System.currentTimeMillis()

            // Create a file attachment
            val attachment =
                FileAttachment(
                    id = 0,
                    userId = userId,
                    fileName = "test.png",
                    contentType = "image/png",
                    fileSize = 1024L,
                    storagePath = "files/$userId/test.png",
                    createdAt = now,
                )

            val createdId = repository.create(attachment)
            assertTrue(createdId > 0, "Created attachment should have a positive ID")

            // Find the attachment by ID
            val foundAttachment = repository.findById(createdId)
            assertNotNull(foundAttachment, "Found attachment should not be null")
            assertEquals("test.png", foundAttachment.fileName, "File name should match")
            assertEquals("image/png", foundAttachment.contentType, "Content type should match")
            assertEquals(userId, foundAttachment.userId, "User ID should match")
        } finally {
            teardown()
        }
    }

    @Test
    fun testFindByUserIdReturnsAttachments() {
        setup()
        try {
            val userId = UUID.randomUUID()
            val now = System.currentTimeMillis()

            // Create two file attachments for the same user
            val attachment1 =
                FileAttachment(
                    id = 0,
                    userId = userId,
                    fileName = "test1.png",
                    contentType = "image/png",
                    fileSize = 1024L,
                    storagePath = "files/$userId/test1.png",
                    createdAt = now,
                )
            val attachment2 =
                FileAttachment(
                    id = 0,
                    userId = userId,
                    fileName = "test.pdf",
                    contentType = "application/pdf",
                    fileSize = 2048L,
                    storagePath = "files/$userId/test.pdf",
                    createdAt = now,
                )

            repository.create(attachment1)
            repository.create(attachment2)

            // Find attachments by user ID
            val attachments = repository.findByUserId(userId)
            assertTrue(attachments.size == 2, "Should find 2 attachments for user")
        } finally {
            teardown()
        }
    }

    @Test
    fun testDeleteById() {
        setup()
        try {
            val userId = UUID.randomUUID()
            val now = System.currentTimeMillis()

            // Create an attachment first
            val attachment =
                FileAttachment(
                    id = 0,
                    userId = userId,
                    fileName = "to-delete.png",
                    contentType = "image/png",
                    fileSize = 1024L,
                    storagePath = "files/$userId/to-delete.png",
                    createdAt = now,
                )
            val createdId = repository.create(attachment)

            // Delete the attachment
            val deletedCount = repository.deleteById(createdId)
            assertEquals(1, deletedCount, "Delete should succeed and delete 1 record")

            // Verify the attachment is deleted
            val foundAttachment = repository.findById(createdId)
            assertEquals(null, foundAttachment, "Attachment should be deleted")
        } finally {
            teardown()
        }
    }

    @Test
    fun testIsAuthorizedForAttachment() {
        setup()
        try {
            val userId = UUID.randomUUID()
            val otherUserId = UUID.randomUUID()
            val now = System.currentTimeMillis()

            // Create an attachment
            val attachment =
                FileAttachment(
                    id = 0,
                    userId = userId,
                    fileName = "test.png",
                    contentType = "image/png",
                    fileSize = 1024L,
                    storagePath = "files/$userId/test.png",
                    createdAt = now,
                )
            val createdId = repository.create(attachment)

            // Verify user owns the attachment
            val isAuthorized = repository.isAuthorizedForAttachment(createdId, userId)
            assertTrue(isAuthorized, "User should be authorized for their own attachment")

            // Verify other user is not authorized
            val otherIsAuthorized = repository.isAuthorizedForAttachment(createdId, otherUserId)
            assertTrue(!otherIsAuthorized, "Other user should not be authorized")
        } finally {
            teardown()
        }
    }

    @Test
    fun testFindByIdReturnsNullForNonExistentAttachment() {
        setup()
        try {
            val nonExistentId = 999L

            val attachment = repository.findById(nonExistentId)
            assertEquals(null, attachment, "Should return null for non-existent attachment")
        } finally {
            teardown()
        }
    }

    @Test
    fun testDeleteByIdReturnsZeroForNonExistentAttachment() {
        setup()
        try {
            val nonExistentId = 999L

            val deletedCount = repository.deleteById(nonExistentId)
            assertEquals(0, deletedCount, "Should return 0 for non-existent attachment")
        } finally {
            teardown()
        }
    }

    @Test
    fun testIsAuthorizedForAttachmentReturnsFalseForNonExistentAttachment() {
        setup()
        try {
            val nonExistentId = 999L
            val userId = UUID.randomUUID()

            val isAuthorized = repository.isAuthorizedForAttachment(nonExistentId, userId)
            assertTrue(!isAuthorized, "Should return false for non-existent attachment")
        } finally {
            teardown()
        }
    }
}
