package com.mel.bubblenotes

import com.fasterxml.jackson.databind.*
import com.mel.bubblenotes.api.notesApi
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
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable
import org.slf4j.event.*

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
                contentType = ContentType.Application.Json
            )
        }
    }
    
    routing {
        // Notes API - must be before static resources to avoid being served as files
        notesApi()
        
        get<Articles> { article ->
            call.respond("List of articles sorted starting from ${article.sort}")
        }
        
        // Serve React frontend from root
        staticResources("/", "static")
    }
}

@Serializable
@Resource("/articles")
class Articles(val sort: String? = "new")
