package com.mel.bubblenotes.api

import com.mel.bubblenotes.UserId
import com.mel.bubblenotes.UserSession
import com.mel.bubblenotes.getInjector
import com.mel.bubblenotes.models.Note
import com.mel.bubblenotes.models.NoteTag
import com.mel.bubblenotes.repositories.NoteRepository
import com.mel.bubblenotes.repositories.NoteTagRepository
import com.mel.bubblenotes.repositories.TagRepository
import com.mel.bubblenotes.repositories.UserRepository
import com.mel.bubblenotes.services.AIEnhancementService
import com.mel.bubblenotes.services.TagService
import com.mel.bubblenotes.services.URLPreview
import com.mel.bubblenotes.services.URLPreviewService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class NoteCreationRequest(val title: String?, val content: String, val tags: List<String>? = null)

@Serializable
data class NoteUpdateRequest(val title: String?, val content: String?, val isPublished: Boolean?, val tags: List<String>? = null)

// NoteRepository instance - should be injected via dependency injection in production
var noteRepository: NoteRepository? = null

// URLPreviewService instance
var urlPreviewService: URLPreviewService? = null

var noteTagRepository: NoteTagRepository? = null
var tagRepository: TagRepository? = null
var tagService: TagService? = null

// AI Enhancement Service instance
var aiEnhancementService: AIEnhancementService? = null

// JSON serializer for URL preview data
private val jsonSerializer =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

fun Route.notesApi() {
    // Protected routes - require authentication via session cookie
    authenticate("session-auth") {
        route("/api/v1/notes") {
            // Create a new note
            post {
                val noteData = call.receive<NoteCreationRequest>()
                val repo =
                    noteRepository ?: return@post call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "Database not configured"),
                    )

                // Get userId from authenticated principal
                val userId =
                    UUID.fromString(
                        call.principal<UserId>()?.id ?: return@post call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Not authenticated"),
                        ),
                    )

                // Validate that the user exists in the database
                val userRepository =
                    try {
                        call.application.getInjector().getInstance(UserRepository::class.java)
                    } catch (e: Exception) {
                        null
                    }
                if (userRepository != null) {
                    val user = userRepository.findById(userId)
                    if (user == null) {
                        // User not found - session is invalid, clear it if possible
                        call.application.log.error("User $userId not found in database - session may be stale")
                        try {
                            call.sessions.clear<UserSession>()
                        } catch (e: Exception) {
                            // Session clear failed, but we still return unauthorized
                            call.application.log.warn("Failed to clear session: ${e.message}")
                        }
                        return@post call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Session expired, please login again"),
                        )
                    }
                }

                // Extract URLs from content and fetch previews
                val urls = URLPreviewService.extractURLs(noteData.content)
                // Log extracted URLs for new note
                call.application.log.info("Extracted URLs for new note: {}", urls)
                val previews = mutableListOf<URLPreview>()
                val service = urlPreviewService
                if (service != null) {
                    runBlocking {
                        urls.forEach { url ->
                            try {
                                val preview = service.fetchPreview(url)
                                previews.add(preview)
                            } catch (e: Exception) {
                                // Skip failed URL previews
                            }
                        }
                    }
                }

                // Store preview data as JSON string
                val previewJson =
                    if (previews.isNotEmpty()) {
                        jsonSerializer.encodeToString(previews)
                    } else {
                        null
                    }

                // Normalize and sanitize tags if provided
                val normalizedTags =
                    (noteData.tags ?: emptyList())
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()

                val note =
                    Note(
                        id = 0, // Will be generated by database
                        userId = userId,
                        title = noteData.title?.takeIf { it.isNotBlank() },
                        content = noteData.content,
                        isPublished = true,
                        tags = normalizedTags,
                        previewData = previewJson,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    )

                val createdId = repo.create(note)
                // Log creation with preview data
                call.application.log.info(
                    "Created note id=$createdId for user $userId with previewData=${
                        previewJson?.take(
                            100,
                        )
                    }",
                )

                // Create AI task for enhancement (async processing)
                val aiService = aiEnhancementService
                if (aiService != null) {
                    try {
                        val taskId = aiService.createAITask(createdId)
                        call.application.log.info("Created AI task $taskId for new note $createdId")
                    } catch (e: Exception) {
                        call.application.log.warn("Failed to create AI task for note $createdId: ${e.message}")
                    }
                } else {
                    call.application.log.debug("AI Enhancement Service not available, skipping AI task creation")
                }

                // Return the full note including preview data for immediate UI update
                val createdNote = note.copy(id = createdId)
                call.respond(HttpStatusCode.Created, createdNote)
            }

            // Get all notes for current user
            get {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val cursor = call.request.queryParameters["cursor"]?.toLongOrNull()

                val repo =
                    noteRepository ?: return@get call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "Database not configured"),
                    )
                // Get userId from authenticated principal
                val userId =
                    UUID.fromString(
                        call.principal<UserId>()?.id ?: return@get call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Not authenticated"),
                        ),
                    )
                val notes =
                    if (call.request.queryParameters.contains("tag")) {
                        val tagName = call.request.queryParameters["tag"]!!.trim()
                        repo.findByUserIdAndTag(userId, tagName, limit, cursor)
                    } else {
                        repo.findByUserId(userId, limit, cursor)
                    }

                call.respond(HttpStatusCode.OK, notes)
            }

            // Get a specific note
            get("/{id}") {
                val id =
                    call.parameters["id"]?.toLongOrNull() ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid note ID"),
                    )

                val repo =
                    noteRepository ?: return@get call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "Database not configured"),
                    )
                val note = repo.findById(id)

                if (note != null) {
                    call.respond(HttpStatusCode.OK, note)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Note not found"))
                }
            }

            // Get URL previews for a specific note
            get("/{id}/previews") {
                val id =
                    call.parameters["id"]?.toLongOrNull() ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid note ID"),
                    )

                val repo =
                    noteRepository ?: return@get call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "Database not configured"),
                    )
                val note = repo.findById(id)

                if (note == null) {
                    return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Note not found"))
                }

                // Return preview data if available
                val previews =
                    if (note.previewData != null) {
                        try {
                            jsonSerializer.decodeFromString<List<URLPreview>>(note.previewData)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }

                call.respond(HttpStatusCode.OK, previews)
            }

            // Update a note
            put("/{id}") {
                val id =
                    call.parameters["id"]?.toLongOrNull() ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid note ID"),
                    )
                val noteData = call.receive<NoteUpdateRequest>()

                val repo =
                    noteRepository ?: return@put call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "Database not configured"),
                    )
                val userId =
                    UUID.fromString(
                        call.principal<UserId>()?.id ?: return@put call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Not authenticated"),
                        ),
                    )

                val existingNote =
                    repo.findById(id) ?: return@put call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Note not found"),
                    )

                // Validate that the user owns the note
                if (existingNote.userId != userId) {
                    return@put call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Not authorized to update this note"),
                    )
                }

                // Extract URLs from new content and fetch previews if content changed
                var previewData = existingNote.previewData
                if (noteData.content != null && noteData.content != existingNote.content) {
                    val urls = URLPreviewService.extractURLs(noteData.content)
                    // Log extracted URLs for note update (id=$id)
                    call.application.log.info("Extracted URLs for note update (id=$id): {}", urls)
                    val previews = mutableListOf<URLPreview>()
                    val service = urlPreviewService
                    if (service != null) {
                        runBlocking {
                            urls.forEach { url ->
                                try {
                                    val preview = service.fetchPreview(url)
                                    previews.add(preview)
                                } catch (e: Exception) {
                                    // Skip failed URL previews
                                }
                            }
                        }
                    }

                    previewData =
                        if (previews.isNotEmpty()) {
                            jsonSerializer.encodeToString(previews)
                        } else {
                            null
                        }
                }

                // Normalize and sanitize tags if provided
                val newTags = noteData.tags?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()

                val updatedNote =
                    existingNote.copy(
                        title = noteData.title?.takeIf { it.isNotBlank() } ?: existingNote.title,
                        content = noteData.content ?: existingNote.content,
                        isPublished = noteData.isPublished ?: existingNote.isPublished,
                        tags = newTags ?: existingNote.tags,
                        previewData = previewData,
                        updatedAt = System.currentTimeMillis(),
                    )

                if (repo.update(updatedNote)) {
                    // Create AI task for enhancement if content changed (async processing)
                    val aiService = aiEnhancementService
                    if (aiService != null && noteData.content != null && noteData.content != existingNote.content) {
                        try {
                            val taskId = aiService.createAITask(id)
                            call.application.log.info("Created AI task $taskId for updated note $id")
                        } catch (e: Exception) {
                            call.application.log.warn("Failed to create AI task for note $id: ${e.message}")
                        }
                    }

                    call.respond(HttpStatusCode.OK, updatedNote)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Failed to update note"))
                }
            }

            // Delete a note
            route("/{id}") {
                delete {
                    val id =
                        call.parameters["id"]?.toLongOrNull() ?: return@delete call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Invalid note ID"),
                        )

                    val repo =
                        noteRepository ?: return@delete call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            mapOf("error" to "Database not configured"),
                        )
                    val userId =
                        UUID.fromString(
                            call.principal<UserId>()?.id ?: return@delete call.respond(
                                HttpStatusCode.Unauthorized,
                                mapOf("error" to "Not authenticated"),
                            ),
                        )

                    if (repo.delete(id, userId)) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Note not found"))
                    }
                }
            }

            // Tag management for a note
            route("/{id}/tags") {
                // Get tags for a note
                get {
                    val noteId =
                        call.parameters["id"]?.toLongOrNull()
                            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid note ID"))
                    val userId =
                        UUID.fromString(
                            call.principal<UserId>()?.id ?: return@get call.respond(
                                HttpStatusCode.Unauthorized,
                                mapOf("error" to "Not authenticated"),
                            ),
                        )
                    val note = noteRepository?.findById(noteId)
                    if (note == null || note.userId != userId) {
                        return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Note not found"))
                    }
                    val tags = tagService?.getTagsForNote(noteId) ?: emptyList()
                    call.respond(HttpStatusCode.OK, tags)
                }
                // Set tags for a note (replace existing)
                post {
                    val noteId =
                        call.parameters["id"]?.toLongOrNull()
                            ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid note ID"))
                    val userId =
                        UUID.fromString(
                            call.principal<UserId>()?.id ?: return@post call.respond(
                                HttpStatusCode.Unauthorized,
                                mapOf("error" to "Not authenticated"),
                            ),
                        )
                    val note = noteRepository?.findById(noteId)
                    if (note == null || note.userId != userId) {
                        return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Note not found"))
                    }

                    val request = call.receive<Map<String, List<String>>>()
                    val tagNames = request["tags"] ?: emptyList()

                    // Clear existing tag associations
                    noteTagRepository?.deleteByNoteId(noteId)

                    // Process each tag: create if needed, then associate
                    for (tagName in tagNames) {
                        val trimmedName = tagName.trim()
                        if (trimmedName.isBlank()) continue

                        // Ensure tag exists (create if needed)
                        val existingTag = tagRepository?.findByNameAndUserId(trimmedName, userId)
                        val tag =
                            if (existingTag != null) {
                                existingTag
                            } else {
                                // Create new tag
                                tagService?.createTag(userId, trimmedName) ?: run {
                                    return@post call.respond(
                                        HttpStatusCode.InternalServerError,
                                        mapOf("error" to "Failed to create tag: $trimmedName"),
                                    )
                                }
                            }

                        // Associate tag with note
                        noteTagRepository?.add(
                            NoteTag(
                                noteId = noteId,
                                tagId = tag.id,
                                userId = note.userId,
                            ),
                        )
                    }

                    call.respond(HttpStatusCode.OK, mapOf("status" to "tags updated"))
                }
                // Delete a specific tag from a note
                delete("/{tagId}") {
                    val noteId =
                        call.parameters["id"]?.toLongOrNull()
                            ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid note ID"))
                    val tagId =
                        call.parameters["tagId"]?.toLongOrNull()
                            ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid tag ID"))
                    val userId =
                        UUID.fromString(
                            call.principal<UserId>()?.id ?: return@delete call.respond(
                                HttpStatusCode.Unauthorized,
                                mapOf("error" to "Not authenticated"),
                            ),
                        )

                    // Verify note ownership
                    val note = noteRepository?.findById(noteId)
                    if (note == null || note.userId != userId) {
                        return@delete call.respond(HttpStatusCode.NotFound, mapOf("error" to "Note not found"))
                    }

                    // Delete the tag association
                    noteTagRepository?.deleteByNoteIdAndTagId(noteId, tagId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }

        // AI Enhancement routes
        route("/api/v1/notes/{id}/ai") {
            // Get AI task status for a note
            get("/status") {
                val noteId =
                    call.parameters["id"]?.toLongOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid note ID"))

                val aiService =
                    aiEnhancementService ?: return@get call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "AI Enhancement Service not available"),
                    )

                // Find the latest AI task for this note
                val result = aiService.getAITaskResultsForNote(noteId)
                if (result != null) {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf(
                            "status" to "completed",
                            "title" to result.aiTitle,
                            "summary" to result.aiSummary,
                            "tags" to result.aiTags,
                        ),
                    )
                } else {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf(
                            "status" to "not_found",
                        ),
                    )
                }
            }

            // Trigger immediate AI enhancement (synchronous)
            post("/enhance") {
                val noteId =
                    call.parameters["id"]?.toLongOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid note ID"))

                val aiService =
                    aiEnhancementService ?: return@post call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "AI Enhancement Service not available"),
                    )

                val repo =
                    noteRepository ?: return@post call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "Database not configured"),
                    )

                val note =
                    repo.findById(noteId) ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Note not found"),
                    )

                try {
                    val result = aiService.enhanceNoteImmediately(note)
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf(
                            "title" to result.aiTitle,
                            "summary" to result.aiSummary,
                            "tags" to result.aiTags,
                        ),
                    )
                } catch (e: Exception) {
                    call.application.log.error("AI enhancement failed for note $noteId: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "AI enhancement failed", "message" to e.message),
                    )
                }
            }
        }
    }
}
