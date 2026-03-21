package com.mel.bubblenotes.api
import com.mel.bubblenotes.UserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

/**
 * REST API for tag queries.
 * Returns unique tag names from the notes.tags JSON column.
 */
fun Route.tagsApi() {
    // All tag routes require authentication
    authenticate("session-auth") {
        // List all unique tags for the current user (from notes.tags JSON column)
        // Uses efficient SQL with jsonb_array_elements_text - O(unique tags) not O(notes)
        get("/api/v1/tags") {
            val userId = UUID.fromString(call.principal<UserId>()?.id ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Not authenticated")))
            val repo = noteRepository ?: return@get call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Note repository not configured"))
            
            // Get unique tags using efficient database query
            val uniqueTags = repo.getUniqueTagsByUserId(userId)
            
            call.respond(HttpStatusCode.OK, uniqueTags)
        }
    }
}
