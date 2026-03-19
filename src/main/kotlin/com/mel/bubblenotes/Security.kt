package com.mel.bubblenotes

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.mel.bubblenotes.models.User
import com.mel.bubblenotes.services.EncryptionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

data class UserId(val id: String)

// Google OAuth configuration
class GoogleOAuthConfig(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val scopes: List<String> = listOf("email", "profile")
) {
    companion object {
        fun fromEnvironment(): GoogleOAuthConfig {
            return GoogleOAuthConfig(
                clientId = System.getenv("GOOGLE_CLIENT_ID") ?: "",
                clientSecret = System.getenv("GOOGLE_CLIENT_SECRET") ?: "",
                redirectUri = System.getenv("GOOGLE_REDIRECT_URI") ?: "http://localhost:8080/auth/google/callback",
                scopes = (System.getenv("GOOGLE_SCOPES") ?: "email,profile").split(",").map { it.trim() }
            )
        }
    }
}

// Session data for encrypted storage
data class UserSession(
    val userId: UUID,
    val email: String,
    val oauthToken: String,
    val expiresAt: Long
)

fun Application.configureSecurity(
    userLookupService: UserLookupService? = null,
    encryptionService: EncryptionService? = null
) {
    val googleConfig = GoogleOAuthConfig.fromEnvironment()
    
    install(Authentication) {
        // Basic authentication for API access (fallback)
        basic("api-auth") {
            validate { credentials ->
                if (credentials.name == "admin" && credentials.password == "secret") {
                    UserId(credentials.name)
                } else null
            }
        }
    }
    
    routing {
        // Google OAuth login endpoint
        get("/auth/google") {
            if (googleConfig.clientId.isEmpty()) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                    "error" to "Google OAuth not configured",
                    "message" to "Please set GOOGLE_CLIENT_ID environment variable"
                ))
                return@get
            }
            
            val state = generateState()
            val scopeString = googleConfig.scopes.joinToString(" ")
            
            val authUrl = buildString {
                append("https://accounts.google.com/o/oauth2/v2/auth")
                append("?client_id=${googleConfig.clientId}")
                append("&redirect_uri=${googleConfig.redirectUri}")
                append("&response_type=code")
                append("&scope=${scopeString}")
                append("&state=$state")
                append("&access_type=offline")
            }
            
            // Store state in cookie for CSRF protection
            call.response.cookies.append(
                name = "oauth_state",
                value = state,
                maxAge = 300, // 5 minutes
                path = "/",
                httpOnly = true
            )
            
            call.respondRedirect(authUrl)
        }
        
        // Google OAuth callback handler
        get("/auth/google/callback") {
            val code = call.request.queryParameters["code"]
            val state = call.request.queryParameters["state"]
            val storedState = call.request.cookies["oauth_state"]
            
            // CSRF protection - validate state parameter
            if (state != storedState) {
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to "Invalid state parameter",
                    "message" to "CSRF validation failed"
                ))
                return@get
            }
            
            // Clear the state cookie
            call.response.cookies.append("oauth_state", "", maxAge = 0)
            
            if (code.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to "Authorization code not provided"
                ))
                return@get
            }
            
            try {
                // Exchange authorization code for ID token
                val idToken = exchangeCodeForIdToken(code, googleConfig)
                
                if (idToken == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf(
                        "error" to "Failed to verify Google authentication"
                    ))
                    return@get
                }
                
                // Verify the ID token
                val jsonFactory: JsonFactory = GsonFactory.getDefaultInstance()
                val verifier = GoogleIdTokenVerifier.Builder(NetHttpTransport(), jsonFactory)
                    .setAudience(listOf(googleConfig.clientId))
                    .build()
                
                val verifiedToken = verifier.verify(idToken)
                
                if (verifiedToken == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf(
                        "error" to "Invalid Google ID token"
                    ))
                    return@get
                }
                
                // Extract user information from the token
                val payload = verifiedToken.payload
                val googleUserId = payload.subject
                val email = payload.get("email") as? String ?: ""
                val name = payload.get("name") as? String ?: ""
                val givenName = payload.get("given_name") as? String
                val familyName = payload.get("family_name") as? String
                val pictureUrl = payload.get("picture") as? String
                
                // Create or lookup user
                val user = if (userLookupService != null) {
                    userLookupService.findOrCreateUser(
                        googleUserId = googleUserId,
                        email = email,
                        name = name,
                        givenName = givenName,
                        familyName = familyName,
                        pictureUrl = pictureUrl,
                        oauthToken = idToken
                    )
                } else {
                    // Fallback: create a new user without persistence
                    User(
                        id = UUID.nameUUIDFromBytes(googleUserId.toByteArray()),
                        email = email,
                        name = name,
                        givenName = givenName,
                        familyName = familyName,
                        pictureUrl = pictureUrl,
                        oauthToken = idToken,
                        encryptionSalt = generateEncryptionSalt(),
                        apiKey = UUID.randomUUID().toString()
                    )
                }
                
                // Create session and encrypt it
                val sessionData = UserSession(
                    userId = user.id,
                    email = user.email,
                    oauthToken = idToken,
                    expiresAt = System.currentTimeMillis() + 86400000 // 24 hours
                )
                
                val encryptedSession = encryptionService?.encryptString(serializeSession(sessionData)) ?: run {
                    call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                        "error" to "Session service not configured"
                    ))
                    return@get
                }
                
                // Set session cookie
                call.response.cookies.append(
                    name = "bubblenotes_session",
                    value = encryptedSession,
                    maxAge = 86400, // 24 hours
                    path = "/",
                    httpOnly = true,
                    secure = false // Set to true in production with HTTPS
                )
                
                // Redirect to dashboard or original URL
                val redirectUrl = call.request.queryParameters["redirect"] ?: "/dashboard"
                call.respondRedirect(redirectUrl)
                
            } catch (e: Exception) {
                call.application.log.error("OAuth callback error", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "error" to "Authentication failed",
                    "message" to e.message
                ))
            }
        }
        
        // Logout endpoint
        post("/auth/logout") {
            // Clear session cookie
            call.response.cookies.append(
                name = "bubblenotes_session",
                value = "",
                maxAge = 0,
                path = "/"
            )
            
            call.respond(HttpStatusCode.OK, mapOf(
                "message" to "Logged out successfully"
            ))
        }
        
        // Get current user info (for frontend)
        get("/api/v1/auth/me") {
            val userId = call.principal<UserId>()?.id
            
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf(
                    "error" to "Not authenticated"
                ))
                return@get
            }
            
            // Return user info without sensitive data
            call.respond(HttpStatusCode.OK, mapOf(
                "authenticated" to true,
                "userId" to userId
            ))
        }
        
        // Token refresh endpoint - exchanges refresh token for new access token
        post("/auth/refresh") {
            val refreshToken = call.request.cookies["google_refresh_token"]
            
            if (refreshToken.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, mapOf(
                    "error" to "No refresh token available",
                    "message" to "Please sign in again"
                ))
                return@post
            }
            
            try {
                val newAccessToken = exchangeRefreshTokenForAccessToken(refreshToken, googleConfig)
                
                if (newAccessToken == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf(
                        "error" to "Failed to refresh token",
                        "message" to "Please sign in again"
                    ))
                    return@post
                }
                
                // Update session with new access token
                val userId = call.principal<UserId>()?.id
                if (userId != null) {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to true,
                        "message" to "Token refreshed successfully"
                    ))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, mapOf(
                        "error" to "Not authenticated"
                    ))
                }
            } catch (e: Exception) {
                call.application.log.error("Token refresh error", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "error" to "Token refresh failed",
                    "message" to e.message
                ))
            }
        }
    }
}

// Serialize session data for storage
private fun serializeSession(session: UserSession): String {
    return "userId=${session.userId};email=${session.email};oauthToken=${session.oauthToken};expiresAt=${session.expiresAt}"
}

// Generate a secure random state for CSRF protection
private fun generateState(): String {
    val secureRandom = SecureRandom()
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

// Exchange authorization code for ID token
private fun exchangeCodeForIdToken(code: String, config: GoogleOAuthConfig): String? {
    try {
        val tokenRequest = GoogleAuthorizationCodeTokenRequest(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            "https://oauth2.googleapis.com/token",
            code,
            config.clientId,
            config.clientSecret,
            config.redirectUri
        )
        
        val tokenResponse = tokenRequest.execute()
        return tokenResponse.idToken
    } catch (e: Exception) {
        println("Error exchanging code for token: ${e.message}")
        return null
    }
}

// Exchange refresh token for new access token
private fun exchangeRefreshTokenForAccessToken(refreshToken: String, config: GoogleOAuthConfig): String? {
    try {
        val tokenRequest = GoogleRefreshTokenRequest(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            refreshToken,
            config.clientId,
            config.clientSecret
        )
        
        val tokenResponse = tokenRequest.execute()
        return tokenResponse.accessToken
    } catch (e: Exception) {
        println("Error refreshing token: ${e.message}")
        return null
    }
}

// Generate encryption salt for user
private fun generateEncryptionSalt(): String {
    val secureRandom = SecureRandom()
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return Base64.getEncoder().encodeToString(bytes)
}

// User lookup service interface
interface UserLookupService {
    fun findOrCreateUser(
        googleUserId: String,
        email: String,
        name: String,
        givenName: String?,
        familyName: String?,
        pictureUrl: String?,
        oauthToken: String
    ): User
}

// Simple user repository interface for OAuth
interface UserRepository {
    fun findByGoogleId(googleId: String): User?
    fun findByEmail(email: String): User?
    fun save(user: User): Boolean
}
