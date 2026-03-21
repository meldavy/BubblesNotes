package com.mel.bubblenotes.repositories

import com.mel.bubblenotes.models.NoteTag
import java.sql.Connection

/**
 * Repository for the many-to-many relationship between notes and tags.
 */
open class NoteTagRepository(private val connection: Connection) {
    /** Add a link between a note and a tag */
    fun add(noteTag: NoteTag) {
        val sql =
            """
            INSERT INTO note_tags (note_id, tag_id, user_id) VALUES (?, ?, ?)
            """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, noteTag.noteId)
            stmt.setLong(2, noteTag.tagId)
            stmt.setObject(3, noteTag.userId)
            stmt.executeUpdate()
        }
    }

    /** Find tag IDs associated with a given note ID */
    fun findTagIdsByNoteId(noteId: Long): List<Long> {
        val sql = "SELECT tag_id FROM note_tags WHERE note_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, noteId)
            val rs = stmt.executeQuery()
            val ids = mutableListOf<Long>()
            while (rs.next()) {
                ids.add(rs.getLong("tag_id"))
            }
            return ids
        }
    }

    /** Find note IDs associated with a given tag ID */
    fun findNoteIdsByTagId(tagId: Long): List<Long> {
        val sql = "SELECT note_id FROM note_tags WHERE tag_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, tagId)
            val rs = stmt.executeQuery()
            val ids = mutableListOf<Long>()
            while (rs.next()) {
                ids.add(rs.getLong("note_id"))
            }
            return ids
        }
    }

    /** Delete all tag links for a note */
    fun deleteByNoteId(noteId: Long) {
        val sql = "DELETE FROM note_tags WHERE note_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, noteId)
            stmt.executeUpdate()
        }
    }

    /** Delete a specific tag link for a note */
    fun deleteByNoteIdAndTagId(
        noteId: Long,
        tagId: Long,
    ) {
        val sql = "DELETE FROM note_tags WHERE note_id = ? AND tag_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, noteId)
            stmt.setLong(2, tagId)
            stmt.executeUpdate()
        }
    }
}
