package com.mel.bubblenotes.api
import com.mel.bubblenotes.UserId

import com.mel.bubblenotes.models.Tag
import com.mel.bubblenotes.models.Note
import com.mel.bubblenotes.services.TagService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

/**
 * REST API for tag management and tag‑based note queries.
 */
fun Route.tagsApi() {
    // All tag routes require authentication
    authenticate("session-auth") {
        // Create a new tag
        post("/api/v1/tags") {
            val userId = UUID.fromString(call.principal<UserId>()?.id ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Not authenticated")))
            val payload = call.receive<Map<String, String>>() // expects {"name": "tagName"}
            val name = payload["name"]?.trim()
            if (name.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Tag name is required"))
            }
            val service = tagService ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Tag service not configured"))
            val tag = service.createTag(userId, name)
            call.respond(HttpStatusCode.Created, tag)
        }

        // List all tags for the current user
        get("/api/v1/tags") {
            val userId = UUID.fromString(call.principal<UserId>()?.id ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Not authenticated")))
            val service = tagService ?: return@get call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Tag service not configured"))
            val tags = service.getTagsByUser(userId)
            call.respond(HttpStatusCode.OK, tags)
        }

        // Delete a tag
        delete("/api/v1/tags/{id}") {
            val tagId = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid tag ID"))
            val userId = UUID.fromString(call.principal<UserId>()?.id ?: return@delete call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Not authenticated")))
            val service = tagService ?: return@delete call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Tag service not configured"))
            val success = service.deleteTag(tagId, userId)
            if (success) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Tag not found or not owned"))
            }
        }
    }
}
