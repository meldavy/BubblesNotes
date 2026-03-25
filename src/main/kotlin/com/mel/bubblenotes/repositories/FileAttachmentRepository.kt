package com.mel.bubblenotes.repositories

import com.mel.bubblenotes.models.FileAttachment
import com.mel.bubblenotes.services.FileAttachmentDataSource
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.util.UUID

/**
 * Repository for FileAttachment entities.
 * Files (images and documents) are independent - stored per user and referenced by path.
 * Must be marked `open` for testability (mocking in unit tests).
 */
open class FileAttachmentRepository(private val dataSource: HikariDataSource) : FileAttachmentDataSource {
    /**
     * Get a fresh connection from the pool for each database operation.
     */
    private fun getConnection(): Connection = dataSource.connection

    /**
     * Create a new file attachment record.
     * @return The generated ID of the new record, or -1 on failure
     */
    override fun create(attachment: FileAttachment): Long {
        val sql =
            """
            INSERT INTO file_attachments 
            (user_id, file_name, content_type, file_size, storage_path, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
                stmt.setString(1, attachment.userId.toString())
                stmt.setString(2, attachment.fileName)
                stmt.setString(3, attachment.contentType)
                stmt.setLong(4, attachment.fileSize)
                stmt.setString(5, attachment.storagePath)
                stmt.setLong(6, attachment.createdAt)

                stmt.executeUpdate()
                stmt.generatedKeys.use { rs ->
                    if (rs.next()) {
                        return rs.getLong(1)
                    }
                }
                throw Exception("Failed to create file attachment")
            }
        }
    }

    /**
     * Find all attachments for a user.
     */
    override fun findByUserId(userId: UUID): List<FileAttachment> {
        val sql =
            """
            SELECT id, user_id, file_name, content_type, file_size, storage_path, created_at
            FROM file_attachments 
            WHERE user_id = ?
            ORDER BY created_at DESC
            """.trimIndent()

        val results = mutableListOf<FileAttachment>()
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, userId.toString())
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        results.add(
                            FileAttachment(
                                id = rs.getLong("id"),
                                userId = UUID.fromString(rs.getString("user_id")),
                                fileName = rs.getString("file_name"),
                                contentType = rs.getString("content_type"),
                                fileSize = rs.getLong("file_size"),
                                storagePath = rs.getString("storage_path"),
                                createdAt = rs.getLong("created_at"),
                            ),
                        )
                    }
                }
            }
        }
        return results
    }

    /**
     * Find a single attachment by ID.
     */
    override fun findById(id: Long): FileAttachment? {
        val sql =
            """
            SELECT id, user_id, file_name, content_type, file_size, storage_path, created_at
            FROM file_attachments 
            WHERE id = ?
            """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return FileAttachment(
                            id = rs.getLong("id"),
                            userId = UUID.fromString(rs.getString("user_id")),
                            fileName = rs.getString("file_name"),
                            contentType = rs.getString("content_type"),
                            fileSize = rs.getLong("file_size"),
                            storagePath = rs.getString("storage_path"),
                            createdAt = rs.getLong("created_at"),
                        )
                    }
                    return null
                }
            }
        }
    }

    /**
     * Delete an attachment by ID.
     * @return Number of rows deleted (0 or 1)
     */
    override fun deleteById(id: Long): Int {
        val sql = "DELETE FROM file_attachments WHERE id = ?"

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, id)
                return stmt.executeUpdate()
            }
        }
    }

    /**
     * Verify user owns this attachment.
     */
    override fun isAuthorizedForAttachment(
        attachmentId: Long,
        userId: UUID,
    ): Boolean {
        val sql =
            """
            SELECT COUNT(*) as count 
            FROM file_attachments 
            WHERE id = ? AND user_id = ?
            """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, attachmentId)
                stmt.setString(2, userId.toString())
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return rs.getInt("count") > 0
                    }
                }
            }
        }
        return false
    }
}
