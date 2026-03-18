package com.mel.bubblenotes

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*

data class UserId(val id: String)

fun Application.configureSecurity() {
    install(Authentication) {
        // Basic authentication for API access
        basic("api-auth") {
            validate { credentials ->
                if (credentials.name == "admin" && credentials.password == "secret") {
                    UserId(credentials.name)
                } else null
            }
        }
    }
}
