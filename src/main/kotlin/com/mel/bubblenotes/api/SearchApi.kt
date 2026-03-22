package com.mel.bubblenotes.api

import com.mel.bubblenotes.getInjector
import com.mel.bubblenotes.models.Note
import com.mel.bubblenotes.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Search API endpoints for full-text search across notes.
 *
 * Supports searching by:
 * - Note title and content
 * - Tag names
 */
fun Route.searchApi() {
    authenticate("jwt-auth") {
        route("/api/v1/search") {
            // Search notes by query string
            post {
                val searchRequest = call.receive<SearchRequest>()
                val repo =
                    noteRepository ?: return@post call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "Database not configured"),
                    )

                // Get userId from authenticated principal
                val userId =
                    UUID.fromString(
                        call.principal<com.mel.bubblenotes.JWTPrincipal>()?.userId?.toString() ?: return@post call.respond(
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
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "Session expired, please login again"),
                        )
                        return@post
                    }
                }

                val limit = searchRequest.limit ?: 20
                val cursor = searchRequest.cursor

                // Perform search
                val results =
                    repo.searchByUserId(
                        userId = userId,
                        query = searchRequest.query,
                        limit = limit,
                        cursor = cursor,
                    )

                call.respond(
                    HttpStatusCode.OK,
                    SearchResponse(
                        results = results,
                        hasMore = results.size == (limit ?: 20),
                    ),
                )
            }
        }
    }
}

@Serializable
data class SearchRequest(
    val query: String,
    val limit: Int? = null,
    val cursor: Long? = null,
)

@Serializable
data class SearchResponse(
    val results: List<Note>,
    val hasMore: Boolean,
)
