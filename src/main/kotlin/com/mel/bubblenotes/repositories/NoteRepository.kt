package com.mel.bubblenotes.repositories

import com.mel.bubblenotes.models.Note
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.util.UUID

open class NoteRepository(private val connection: Connection) {
    private val json = Json { ignoreUnknownKeys = true }

    fun create(note: Note): Long {
        val sql =
            """
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
        val sql =
            """
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
                    aiTags = rs.getString("ai_tags")?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) } ?: emptyList(),
                    lastVersionId = rs.getLongOrNull("last_version_id"),
                    previewData = rs.getString("preview_data"),
                    createdAt = rs.getLong("created_at"),
                    updatedAt = rs.getLong("updated_at"),
                )
            }
            return null
        }
    }

    fun findByUserId(
        userId: UUID,
        limit: Int = 20,
        cursor: Long? = null,
    ): List<Note> {
        val sql =
            if (cursor != null) {
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
                    add(
                        Note(
                            id = rs.getLong("id"),
                            userId = rs.getObject("user_id", UUID::class.java),
                            title = rs.getString("title"),
                            content = rs.getString("content"),
                            isPublished = rs.getBoolean("is_published"),
                            tags = rs.getString("tags")?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) } ?: emptyList(),
                            aiTitle = rs.getString("ai_title"),
                            aiSummary = rs.getString("ai_summary"),
                            aiTags = rs.getString("ai_tags")?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) } ?: emptyList(),
                            lastVersionId = rs.getLongOrNull("last_version_id"),
                            previewData = rs.getString("preview_data"),
                            createdAt = rs.getLong("created_at"),
                            updatedAt = rs.getLong("updated_at"),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Retrieve notes for a user filtered by a set of note IDs.
     */
    fun findByUserIdAndIds(
        userId: UUID,
        noteIds: List<Long>,
        limit: Int = 20,
        cursor: Long? = null,
    ): List<Note> {
        if (noteIds.isEmpty()) return emptyList()
        // Build a parameter placeholder list for IN clause
        val placeholders = noteIds.joinToString(",") { "?" }
        val sql =
            if (cursor != null) {
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
                    add(
                        Note(
                            id = rs.getLong("id"),
                            userId = rs.getObject("user_id", UUID::class.java),
                            title = rs.getString("title"),
                            content = rs.getString("content"),
                            isPublished = rs.getBoolean("is_published"),
                            tags = rs.getString("tags")?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) } ?: emptyList(),
                            aiTitle = rs.getString("ai_title"),
                            aiSummary = rs.getString("ai_summary"),
                            aiTags = rs.getString("ai_tags")?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) } ?: emptyList(),
                            lastVersionId = rs.getLongOrNull("last_version_id"),
                            previewData = rs.getString("preview_data"),
                            createdAt = rs.getLong("created_at"),
                            updatedAt = rs.getLong("updated_at"),
                        ),
                    )
                }
            }
        }
    }

    fun update(note: Note): Boolean {
        val sql =
            """
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
     * Update AI enhancements for a note.
     * @param noteId The note ID
     * @param aiTitle The AI-generated title (optional)
     * @param aiSummary The AI-generated summary (optional)
     * @param aiTags The AI-generated tags (optional)
     * @return true if updated, false if not found
     */
    fun updateAIEnhancements(
        noteId: Long,
        aiTitle: String? = null,
        aiSummary: String? = null,
        aiTags: List<String>? = null,
    ): Boolean {
        val sql =
            """
            UPDATE notes
            SET ai_title = COALESCE(?, ai_title),
                ai_summary = COALESCE(?, ai_summary),
                ai_tags = COALESCE(?, ai_tags),
                updated_at = ?
            WHERE id = ?
            """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, aiTitle)
            stmt.setString(2, aiSummary)
            stmt.setString(3, aiTags?.let { json.encodeToString(it) })
            stmt.setLong(4, System.currentTimeMillis())
            stmt.setLong(5, noteId)

            return stmt.executeUpdate() > 0
        }
    }

    /**
     * Retrieve notes by user and tag name using embedded JSON tags.
     * This uses a simple LIKE match on the JSON array to avoid DB-specific JSON operators.
     */
    fun findByUserIdAndTag(
        userId: UUID,
        tagName: String,
        limit: Int = 20,
        cursor: Long? = null,
    ): List<Note> {
        val pattern = "%\"${tagName.trim()}\"%" // matches exact JSON string element
        val sql =
            if (cursor != null) {
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
                            tags =
                                rs.getString("tags")?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) }
                                    ?: emptyList(),
                            aiTitle = rs.getString("ai_title"),
                            aiSummary = rs.getString("ai_summary"),
                            aiTags = rs.getString("ai_tags")?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) } ?: emptyList(),
                            lastVersionId = rs.getLongOrNull("last_version_id"),
                            previewData = rs.getString("preview_data"),
                            createdAt = rs.getLong("created_at"),
                            updatedAt = rs.getLong("updated_at"),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Search notes by query string matching title, content, and tags.
     * Uses LIKE-based search for compatibility with both PostgreSQL and H2.
     * For PostgreSQL with tsvector, this could be enhanced to use full-text search.
     */
    fun searchByUserId(
        userId: UUID,
        query: String,
        limit: Int = 20,
        cursor: Long? = null,
    ): List<Note> {
        if (query.isBlank()) {
            // If query is empty, return all notes (fallback to regular listing)
            return findByUserId(userId, limit, cursor)
        }

        // Normalize query for search (trim and lowercase for case-insensitive match)
        val searchQuery = query.trim().lowercase()

        // Build search patterns for title, content, and tags
        // Using OR to match any of the fields
        val titlePattern = "%$searchQuery%"
        val contentPattern = "%$searchQuery%"
        val tagsPattern = "%\"${searchQuery}\"%"

        val sql =
            if (cursor != null) {
                """
                SELECT id, user_id, title, content, is_published, tags, ai_title, ai_summary, ai_tags, last_version_id, preview_data, created_at, updated_at
                FROM notes
                WHERE user_id = ?
                  AND id < ?
                  AND (
                      LOWER(title) LIKE ?
                      OR LOWER(content) LIKE ?
                      OR tags LIKE ?
                  )
                ORDER BY id DESC
                LIMIT ?
                """.trimIndent()
            } else {
                """
                SELECT id, user_id, title, content, is_published, tags, ai_title, ai_summary, ai_tags, last_version_id, preview_data, created_at, updated_at
                FROM notes
                WHERE user_id = ?
                  AND (
                      LOWER(title) LIKE ?
                      OR LOWER(content) LIKE ?
                      OR tags LIKE ?
                  )
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
            stmt.setString(idx++, titlePattern)
            stmt.setString(idx++, contentPattern)
            stmt.setString(idx++, tagsPattern)
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
                            tags = rs.getString("tags")?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) } ?: emptyList(),
                            aiTitle = rs.getString("ai_title"),
                            aiSummary = rs.getString("ai_summary"),
                            aiTags = rs.getString("ai_tags")?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) } ?: emptyList(),
                            lastVersionId = rs.getLongOrNull("last_version_id"),
                            previewData = rs.getString("preview_data"),
                            createdAt = rs.getLong("created_at"),
                            updatedAt = rs.getLong("updated_at"),
                        ),
                    )
                }
            }
        }
    }

    fun delete(
        id: Long,
        userId: UUID,
    ): Boolean {
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

    /**
     * Get unique tag names from the notes.tags JSON column for a user.
     * Uses database-specific SQL for efficiency:
     * - PostgreSQL: Uses jsonb_array_elements_text() for O(unique tags) complexity
     * - H2: Falls back to batch fetching with in-memory deduplication
     * Returns tags sorted alphabetically.
     */
    fun getUniqueTagsByUserId(userId: UUID): List<String> {
        // Try PostgreSQL-specific efficient query first
        return try {
            val sql =
                """
                SELECT DISTINCT tag_name
                FROM (
                    SELECT jsonb_array_elements_text(tags::jsonb) as tag_name
                    FROM notes
                    WHERE user_id = ?
                      AND tags IS NOT NULL
                      AND tags != '[]'
                    UNION
                    SELECT jsonb_array_elements_text(ai_tags::jsonb) as tag_name
                    FROM notes
                    WHERE user_id = ?
                      AND ai_tags IS NOT NULL
                      AND ai_tags != '[]'
                ) AS tags_expanded
                ORDER BY tag_name
                """.trimIndent()

            connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setObject(2, userId)
                val rs = stmt.executeQuery()
                buildList {
                    while (rs.next()) {
                        add(rs.getString("tag_name"))
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback for H2: fetch notes in batches and extract tags in memory
            fallbackGetUniqueTagsByUserId(userId)
        }
    }

    /**
     * Fallback implementation for databases without jsonb_array_elements_text support.
     * Fetches notes in batches to minimize memory usage.
     */
    private fun fallbackGetUniqueTagsByUserId(userId: UUID): List<String> {
        val batchSize = 500
        val allTags = mutableSetOf<String>()
        var lastId = 0L
        var hasMore = true

        while (hasMore) {
            val sql =
                """
                SELECT id, tags, ai_tags FROM notes
                WHERE user_id = ?
                  AND (tags IS NOT NULL AND tags != '[]' OR ai_tags IS NOT NULL AND ai_tags != '[]')
                  AND id > ?
                ORDER BY id
                LIMIT ?
                """.trimIndent()

            connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setObject(2, lastId)
                stmt.setInt(3, batchSize)
                val rs = stmt.executeQuery()

                var count = 0
                while (rs.next()) {
                    count++
                    lastId = rs.getLong("id")

                    // Extract user-defined tags
                    val tagsJson = rs.getString("tags")
                    tagsJson?.let { jsonStr ->
                        runCatching {
                            json.decodeFromString<List<String>>(jsonStr)
                                .filter { it.isNotBlank() }
                                .forEach { allTags.add(it) }
                        }
                    }

                    // Extract AI-generated tags
                    val aiTagsJson = rs.getString("ai_tags")
                    aiTagsJson?.let { jsonStr ->
                        runCatching {
                            json.decodeFromString<List<String>>(jsonStr)
                                .filter { it.isNotBlank() }
                                .forEach { allTags.add(it) }
                        }
                    }
                }
                hasMore = count == batchSize
            }
        }

        return allTags.sorted()
    }
}
