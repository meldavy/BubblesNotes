package com.mel.bubblenotes.services

import com.mel.bubblenotes.models.Note
import com.mel.bubblenotes.models.Tag
import com.mel.bubblenotes.repositories.NoteRepository
import com.mel.bubblenotes.repositories.NoteTagRepository
import com.mel.bubblenotes.repositories.TagRepository
import java.util.UUID

/**
 * Service layer for tag management and tag-based note queries.
 * All repositories are injected via Guice.
 */
class TagService(
    private val noteTagRepository: NoteTagRepository,
    private val tagRepository: TagRepository,
    private val noteRepository: NoteRepository,
) {
    /** Create a new tag for a user, ensuring uniqueness per user. */
    fun createTag(
        userId: UUID,
        name: String,
    ): Tag {
        // Check if tag already exists
        val existing = tagRepository.findByNameAndUserId(name, userId)
        if (existing != null) return existing
        val tag = Tag(id = 0, userId = userId, name = name, createdAt = System.currentTimeMillis())
        val generatedId = tagRepository.create(tag)
        return tag.copy(id = generatedId)
    }

    /** Retrieve all tags for a user, ordered by name. */
    fun getTagsByUser(userId: UUID): List<Tag> = tagRepository.findByUserId(userId)

    /** Delete a tag owned by the user. */
    fun deleteTag(
        tagId: Long,
        userId: UUID,
    ): Boolean {
        val tag = tagRepository.findById(tagId) ?: return false
        if (tag.userId != userId) return false
        // Associations are removed via cascade or can be handled separately
        return tagRepository.delete(tagId, userId)
    }

    /** Get notes that have a given tag name for the user. */
    fun getNotesByTag(
        userId: UUID,
        tagName: String,
        limit: Int,
        cursor: Long?,
    ): List<Note> {
        val tag = tagRepository.findByNameAndUserId(tagName, userId) ?: return emptyList()
        val noteIds = noteTagRepository.findNoteIdsByTagId(tag.id)
        if (noteIds.isEmpty()) return emptyList()
        return noteRepository.findByUserIdAndIds(userId, noteIds, limit, cursor)
    }

    /** Get tags associated with a specific note. */
    fun getTagsForNote(noteId: Long): List<Tag> {
        val tagIds = noteTagRepository.findTagIdsByNoteId(noteId)
        if (tagIds.isEmpty()) return emptyList()
        return tagIds.mapNotNull { tagRepository.findById(it) }
    }
}
