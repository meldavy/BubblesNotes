package com.mel.bubblenotes

import com.fasterxml.jackson.databind.*
import com.mel.bubblenotes.api.notesApi
import com.mel.bubblenotes.api.searchApi
import com.mel.bubblenotes.api.tagsApi
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.serialization.jackson.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import org.slf4j.event.*
import java.io.File

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
        authenticate("session-auth") {
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
        staticResources("/", "static")

        // SPA catch-all route: serve index.html for any non-API, non-file paths
        // This MUST come after staticResources so actual files are served correctly
        get("/{path:.+}") {
            val path = call.parameters["path"] ?: ""

            // Only serve index.html for paths that don't have file extensions
            // and aren't already handled by API routes
            if (!path.contains(".") && !path.startsWith("api/")) {
                val indexFile = File("src/main/resources/static/index.html")
                if (indexFile.exists()) {
                    call.respondFile(indexFile)
                } else {
                    call.respondText("Frontend not found", status = HttpStatusCode.NotFound)
                }
            } else {
                call.respondText("Not found", status = HttpStatusCode.NotFound)
            }
        }
    }
}

@Serializable
@Resource("/articles")
class Articles(val sort: String? = "new")
