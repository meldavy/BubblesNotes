package com.mel.bubblenotes.repositories

import com.mel.bubblenotes.models.Note
import java.sql.Connection
import java.util.UUID
import java.util.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

open class NoteRepository(private val connection: Connection) {
    private val json = Json { ignoreUnknownKeys = true }
    
    fun create(note: Note): Long {
        val sql = """
            INSERT INTO notes (user_id, title, content, is_published, tags, preview_data, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        
        connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setObject(1, note.userId)
            stmt.setString(2, note.title)
            stmt.setString(3, note.content)
            stmt.setBoolean(4, note.isPublished)
            stmt.setString(5, json.encodeToString(note.tags))
            stmt.setString(6, note.previewData)
            stmt.setLong(7, note.createdAt)
            stmt.setLong(8, note.updatedAt)
            
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
            SELECT id, user_id, title, content, is_published, tags, ai_title, ai_summary, ai_tags, last_version_id, preview_data, created_at, updated_at
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
                    tags = rs.getString("tags")?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) } ?: emptyList(),
                    aiTitle = rs.getString("ai_title"),
                    aiSummary = rs.getString("ai_summary"),
                    aiTags = rs.getObject("ai_tags", List::class.java) as? List<String>,
                    lastVersionId = rs.getLongOrNull("last_version_id"),
                    previewData = rs.getString("preview_data"),
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
                SELECT id, user_id, title, content, is_published, tags, ai_title, ai_summary, ai_tags, last_version_id, preview_data, created_at, updated_at
                FROM notes
                WHERE user_id = ? AND id < ?
                ORDER BY id DESC
                LIMIT ?
            """.trimIndent()
        } else {
            """
                SELECT id, user_id, title, content, is_published, tags, ai_title, ai_summary, ai_tags, last_version_id, preview_data, created_at, updated_at
                FROM notes
                WHERE user_id = ?
                ORDER BY id DESC
                LIMIT ?
            """.trimIndent()
        }
        connection.prepareStatement(sql).use { stmt ->
            var idx = 1
            stmt.setObject(idx++, userId)
            if (cursor != null) {
                stmt.setLong(idx++, cursor)
            }
            stmt.setInt(idx, limit)
            val rs = stmt.executeQuery()
            return buildList {
                while (rs.next()) {
                    add(Note(
                        id = rs.getLong("id"),
                        userId = rs.getObject("user_id", UUID::class.java),
                        title = rs.getString("title"),
                        content = rs.getString("content"),
                        isPublished = rs.getBoolean("is_published"),
                        tags = rs.getString("tags")?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) } ?: emptyList(),
                        aiTitle = rs.getString("ai_title"),
                        aiSummary = rs.getString("ai_summary"),
                        aiTags = rs.getObject("ai_tags", List::class.java) as? List<String>,
                        lastVersionId = rs.getLongOrNull("last_version_id"),
                        previewData = rs.getString("preview_data"),
                        createdAt = rs.getLong("created_at"),
                        updatedAt = rs.getLong("updated_at")
                    ))
                }
            }
        }
    }

    /**
     * Retrieve notes for a user filtered by a set of note IDs.
     */
    fun findByUserIdAndIds(userId: UUID, noteIds: List<Long>, limit: Int = 20, cursor: Long? = null): List<Note> {
        if (noteIds.isEmpty()) return emptyList()
        // Build a parameter placeholder list for IN clause
        val placeholders = noteIds.joinToString(",") { "?" }
        val sql = if (cursor != null) {
            """
                SELECT id, user_id, title, content, is_published, tags, ai_title, ai_summary, ai_tags, last_version_id, preview_data, created_at, updated_at
                FROM notes
                WHERE user_id = ? AND id < ? AND id IN ($placeholders)
                ORDER BY id DESC
                LIMIT ?
            """.trimIndent()
        } else {
            """
                SELECT id, user_id, title, content, is_published, tags, ai_title, ai_summary, ai_tags, last_version_id, preview_data, created_at, updated_at
                FROM notes
                WHERE user_id = ? AND id IN ($placeholders)
                ORDER BY id DESC
                LIMIT ?
            """.trimIndent()
        }
        connection.prepareStatement(sql).use { stmt ->
            var idx = 1
            stmt.setObject(idx++, userId)
            if (cursor != null) {
                stmt.setLong(idx++, cursor)
            }
            // set noteIds parameters
            for (id in noteIds) {
                stmt.setLong(idx++, id)
            }
            stmt.setInt(idx, limit)
            val rs = stmt.executeQuery()
            return buildList {
                while (rs.next()) {
                    add(Note(
                        id = rs.getLong("id"),
                        userId = rs.getObject("user_id", UUID::class.java),
                        title = rs.getString("title"),
                        content = rs.getString("content"),
                        isPublished = rs.getBoolean("is_published"),
                        tags = rs.getString("tags")?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) } ?: emptyList(),
                        aiTitle = rs.getString("ai_title"),
                        aiSummary = rs.getString("ai_summary"),
                        aiTags = rs.getObject("ai_tags", List::class.java) as? List<String>,
                        lastVersionId = rs.getLongOrNull("last_version_id"),
                        previewData = rs.getString("preview_data"),
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
            SET title = ?, content = ?, is_published = ?, tags = ?, preview_data = ?, updated_at = ?
            WHERE id = ? AND user_id = ?
        """.trimIndent()
        
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, note.title)
            stmt.setString(2, note.content)
            stmt.setBoolean(3, note.isPublished)
            stmt.setString(4, json.encodeToString(note.tags))
            stmt.setString(5, note.previewData)
            stmt.setLong(6, note.updatedAt)
            stmt.setLong(7, note.id)
            stmt.setObject(8, note.userId)
            
            return stmt.executeUpdate() > 0
        }
    }

    /**
     * Retrieve notes by user and tag name using embedded JSON tags.
     * This uses a simple LIKE match on the JSON array to avoid DB-specific JSON operators.
     */
    fun findByUserIdAndTag(userId: UUID, tagName: String, limit: Int = 20, cursor: Long? = null): List<Note> {
        val pattern = "%\"${tagName.trim()}\"%" // matches exact JSON string element
        val sql = if (cursor != null) {
            """
                SELECT id, user_id, title, content, is_published, tags, ai_title, ai_summary, ai_tags, last_version_id, preview_data, created_at, updated_at
                FROM notes
                WHERE user_id = ? AND id < ? AND tags LIKE ?
                ORDER BY id DESC
                LIMIT ?
            """.trimIndent()
        } else {
            """
                SELECT id, user_id, title, content, is_published, tags, ai_title, ai_summary, ai_tags, last_version_id, preview_data, created_at, updated_at
                FROM notes
                WHERE user_id = ? AND tags LIKE ?
                ORDER BY id DESC
                LIMIT ?
            """.trimIndent()
        }

        connection.prepareStatement(sql).use { stmt ->
            var idx = 1
            stmt.setObject(idx++, userId)
            if (cursor != null) {
                stmt.setLong(idx++, cursor)
            }
            stmt.setString(idx++, pattern)
            stmt.setInt(idx, limit)
            val rs = stmt.executeQuery()
            return buildList {
                while (rs.next()) {
                    add(
                        Note(
                            id = rs.getLong("id"),
                            userId = rs.getObject("user_id", UUID::class.java),
                            title = rs.getString("title"),
                            content = rs.getString("content"),
                            isPublished = rs.getBoolean("is_published"),
                            tags = rs.getString("tags")?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) }
                                ?: emptyList(),
                            aiTitle = rs.getString("ai_title"),
                            aiSummary = rs.getString("ai_summary"),
                            aiTags = rs.getObject("ai_tags", List::class.java) as? List<String>,
                            lastVersionId = rs.getLongOrNull("last_version_id"),
                            previewData = rs.getString("preview_data"),
                            createdAt = rs.getLong("created_at"),
                            updatedAt = rs.getLong("updated_at")
                        )
                    )
                }
            }
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
