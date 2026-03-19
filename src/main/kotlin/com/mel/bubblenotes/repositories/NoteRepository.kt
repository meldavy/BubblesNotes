package com.mel.bubblenotes.repositories

import com.mel.bubblenotes.models.Note
import java.sql.Connection
import java.util.UUID
import java.util.*

open class NoteRepository(private val connection: Connection) {
    
    fun create(note: Note): Long {
        val sql = """
            INSERT INTO notes (user_id, title, content, is_published, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        
        connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setObject(1, note.userId)
            stmt.setString(2, note.title)
            stmt.setString(3, note.content)
            stmt.setBoolean(4, note.isPublished)
            stmt.setLong(5, note.createdAt)
            stmt.setLong(6, note.updatedAt)
            
            stmt.executeUpdate()
            val rs = stmt.generatedKeys
            if (rs.next()) {
                return rs.getLong(1)
            }
            throw Exception("Failed to create note")
        }
    }
    
    fun findById(id: Long): Note? {
        val sql = """
            SELECT id, user_id, title, content, is_published, ai_title, ai_summary, ai_tags, last_version_id, created_at, updated_at
            FROM notes WHERE id = ?
        """.trimIndent()
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, id)
            val rs = stmt.executeQuery()
            
            if (rs.next()) {
                return Note(
                    id = rs.getLong("id"),
                    userId = rs.getObject("user_id", UUID::class.java),
                    title = rs.getString("title"),
                    content = rs.getString("content"),
                    isPublished = rs.getBoolean("is_published"),
                    aiTitle = rs.getString("ai_title"),
                    aiSummary = rs.getString("ai_summary"),
                    aiTags = rs.getObject("ai_tags", List::class.java) as? List<String>,
                    lastVersionId = rs.getLongOrNull("last_version_id"),
                    createdAt = rs.getLong("created_at"),
                    updatedAt = rs.getLong("updated_at")
                )
            }
            return null
        }
    }
    
    fun findByUserId(userId: UUID, limit: Int = 20, cursor: Long? = null): List<Note> {
        val sql = if (cursor != null) {
            """
                SELECT id, user_id, title, content, is_published, ai_title, ai_summary, ai_tags, last_version_id, created_at, updated_at
                FROM notes 
                WHERE user_id = ? AND id < ?
                ORDER BY id DESC
                LIMIT ?
            """.trimIndent()
        } else {
            """
                SELECT id, user_id, title, content, is_published, ai_title, ai_summary, ai_tags, last_version_id, created_at, updated_at
                FROM notes 
                WHERE user_id = ?
                ORDER BY id DESC
                LIMIT ?
            """.trimIndent()
        }
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, userId)
            if (cursor != null) {
                stmt.setLong(2, cursor)
                stmt.setInt(3, limit)
            } else {
                stmt.setInt(2, limit)
            }
            
            val rs = stmt.executeQuery()
            return buildList {
                while (rs.next()) {
                    add(Note(
                        id = rs.getLong("id"),
                        userId = rs.getObject("user_id", UUID::class.java),
                        title = rs.getString("title"),
                        content = rs.getString("content"),
                        isPublished = rs.getBoolean("is_published"),
                        aiTitle = rs.getString("ai_title"),
                        aiSummary = rs.getString("ai_summary"),
                        aiTags = rs.getObject("ai_tags", List::class.java) as? List<String>,
                        lastVersionId = rs.getLongOrNull("last_version_id"),
                        createdAt = rs.getLong("created_at"),
                        updatedAt = rs.getLong("updated_at")
                    ))
                }
            }
        }
    }
    
    fun update(note: Note): Boolean {
        val sql = """
            UPDATE notes 
            SET title = ?, content = ?, is_published = ?, updated_at = ?
            WHERE id = ? AND user_id = ?
        """.trimIndent()
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, note.title)
            stmt.setString(2, note.content)
            stmt.setBoolean(3, note.isPublished)
            stmt.setLong(4, note.updatedAt)
            stmt.setLong(5, note.id)
            stmt.setObject(6, note.userId)
            
            return stmt.executeUpdate() > 0
        }
    }
    
    fun delete(id: Long, userId: UUID): Boolean {
        val sql = "DELETE FROM notes WHERE id = ? AND user_id = ?"
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, id)
            stmt.setObject(2, userId)
            return stmt.executeUpdate() > 0
        }
    }
    
    fun countByUserId(userId: UUID): Int {
        val sql = "SELECT COUNT(*) FROM notes WHERE user_id = ?"
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, userId)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return rs.getInt(1)
            }
            return 0
        }
    }
}
