package com.mel.bubblenotes

import com.mel.bubblenotes.api.notesApi
import com.mel.bubblenotes.api.searchApi
import com.mel.bubblenotes.api.tagsApi
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.resources.*
import io.ktor.server.resources.Resources
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Application.configureRouting() {
    install(Resources)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Log the full exception details internally for debugging
            call.application.log.error("Internal server error: ${cause.message}", cause)

            // Return generic error message to clients without exposing internal details
            call.respondText(
                text = """{"error": "An internal error occurred", "status": 500}""",
                status = HttpStatusCode.InternalServerError,
                contentType = ContentType.Application.Json,
            )
        }
    }

    routing {
        // Configure authentication challenge handling for protected routes
        // When a user is not authenticated on a protected route, return 401 instead of redirecting
        authenticate("jwt-auth") {
            // This block will handle all protected API routes
            // If authentication fails, Ktor will automatically return 401 Unauthorized
        }

        // Notes API - must be before static resources to avoid being served as files
        notesApi()
        // Tags API
        tagsApi()
        // Search API
        searchApi()

        get<Articles> { article ->
            call.respond("List of articles sorted starting from ${article.sort}")
        }

        // Serve React frontend static files (CSS, JS, images, etc.) first
        singlePageApplication {
            useResources = true
            filesPath = "static"
            defaultPage = "index.html"
        }
    }
}

@Serializable
@Resource("/articles")
class Articles(val sort: String? = "new")
