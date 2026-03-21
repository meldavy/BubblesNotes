package com.mel.bubblenotes.repositories

import com.mel.bubblenotes.models.Attachment
import java.sql.Connection
import java.util.UUID

open class AttachmentRepository(private val connection: Connection) {
    fun create(attachment: Attachment): Long {
        val sql =
            """
            INSERT INTO attachments (note_id, user_id, file_name, content_type, file_size, storage_path, encrypted_data, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

        connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setLong(1, attachment.noteId)
            stmt.setObject(2, attachment.userId)
            stmt.setString(3, attachment.fileName)
            stmt.setString(4, attachment.contentType)
            stmt.setLong(5, attachment.fileSize)
            stmt.setString(6, attachment.storagePath)
            if (attachment.encryptedData != null) {
                stmt.setBytes(7, attachment.encryptedData)
            } else {
                stmt.setNull(7, java.sql.Types.BLOB)
            }
            stmt.setLong(8, attachment.createdAt)

            stmt.executeUpdate()
            val rs = stmt.generatedKeys
            if (rs.next()) {
                return rs.getLong(1)
            }
            throw Exception("Failed to create attachment")
        }
    }

    fun findByNoteId(noteId: Long): List<Attachment> {
        val sql =
            """
            SELECT id, note_id, user_id, file_name, content_type, file_size, storage_path, encrypted_data, created_at
            FROM attachments WHERE note_id = ?
            ORDER BY created_at DESC
            """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, noteId)
            val rs = stmt.executeQuery()

            return buildList {
                while (rs.next()) {
                    add(
                        Attachment(
                            id = rs.getLong("id"),
                            noteId = rs.getLong("note_id"),
                            userId = rs.getObject("user_id", UUID::class.java),
                            fileName = rs.getString("file_name"),
                            contentType = rs.getString("content_type"),
                            fileSize = rs.getLong("file_size"),
                            storagePath = rs.getString("storage_path"),
                            encryptedData = rs.getBytes("encrypted_data"),
                            createdAt = rs.getLong("created_at"),
                        ),
                    )
                }
            }
        }
    }

    fun findById(id: Long): Attachment? {
        val sql =
            """
            SELECT id, note_id, user_id, file_name, content_type, file_size, storage_path, encrypted_data, created_at
            FROM attachments WHERE id = ?
            """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, id)
            val rs = stmt.executeQuery()

            if (rs.next()) {
                return Attachment(
                    id = rs.getLong("id"),
                    noteId = rs.getLong("note_id"),
                    userId = rs.getObject("user_id", UUID::class.java),
                    fileName = rs.getString("file_name"),
                    contentType = rs.getString("content_type"),
                    fileSize = rs.getLong("file_size"),
                    storagePath = rs.getString("storage_path"),
                    encryptedData = rs.getBytes("encrypted_data"),
                    createdAt = rs.getLong("created_at"),
                )
            }
            return null
        }
    }

    fun delete(
        id: Long,
        userId: UUID,
    ): Boolean {
        val sql = "DELETE FROM attachments WHERE id = ? AND user_id = ?"

        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, id)
            stmt.setObject(2, userId)
            return stmt.executeUpdate() > 0
        }
    }
}
