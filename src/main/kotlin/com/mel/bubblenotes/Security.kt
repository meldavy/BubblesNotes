package com.mel.bubblenotes

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.mel.bubblenotes.models.User
import com.mel.bubblenotes.repositories.UserRepository
import com.mel.bubblenotes.services.EncryptionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class UserId(val id: String)

// User lookup service interface for OAuth user management
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

// Session data for encrypted storage - must be serializable for Ktor sessions
@kotlinx.serialization.Serializable
data class UserSession(
    val userId: String,  // Changed from UUID to String for serialization
    val email: String,
    val oauthToken: String,
    val expiresAt: Long
)

// Google OAuth configuration
class GoogleOAuthConfig(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val scopes: List<String> = listOf("email", "profile")
)

fun Application.configureSecurity(
    userLookupService: UserLookupService? = null,
    encryptionService: EncryptionService? = null
) {
    // Retrieve userRepository from Guice DI if available
    val userRepository = try {
        getInjector().getInstance(UserRepository::class.java)
    } catch (e: Exception) {
        environment.log.warn("UserRepository not available in DI container, user persistence disabled")
        null
    }
    val config = environment.config
    
    // Read Google OAuth configuration from application.yaml with fallback defaults
    val googleClientId = try {
        config.property("oauth.google.client-id").getString()
    } catch (e: Exception) {
        environment.log.warn("OAuth client-id not configured, using empty string")
        ""
    }
    val googleClientSecret = try {
        config.property("oauth.google.client-secret").getString()
    } catch (e: Exception) {
        environment.log.warn("OAuth client-secret not configured, using empty string")
        ""
    }
    val googleRedirectUri = try {
        config.property("oauth.google.redirect-uri").getString()
    } catch (e: Exception) {
        environment.log.warn("OAuth redirect-uri not configured, using default")
        "http://localhost:8080/auth/google/callback"
    }
    val googleScopes = try {
        config.property("oauth.google.scopes").getString()
            .split(",").map { it.trim() }
    } catch (e: Exception) {
        environment.log.warn("OAuth scopes not configured, using default")
        listOf("email", "profile")
    }
    
    val googleConfig = GoogleOAuthConfig(
        clientId = googleClientId,
        clientSecret = googleClientSecret,
        redirectUri = googleRedirectUri,
        scopes = googleScopes
    )
    
    // Get encryption key for JWT signing with fallback
    val jwtSecretKey = try {
        config.property("encryption.session-key").getString().toByteArray()
    } catch (e: Exception) {
        environment.log.warn("Session encryption key not configured, using default")
        // 32-byte key for AES-256 encryption
        "dev-session-key-32bytes-for-aes256!!".toByteArray()
    }
    
    // Install Sessions plugin for cookie-based session management
    install(Sessions) {
        cookie<UserSession>("session-auth") {
            cookie.path = "/"  // Essential: Make cookie available to entire app
            cookie.maxAgeInSeconds = 86400 // 24 hours
            cookie.httpOnly = true
            cookie.secure = false // Set to true in production with HTTPS
            // Essential for OAuth redirects: Lax allows cookie after redirect from Google
            cookie.extensions["SameSite"] = "Lax"
            
            // Use encryption for session data to protect sensitive information
            // SessionTransportTransformerEncrypt requires:
            // - encryptKey: 16, 24, or 32 bytes for AES-128/192/256
            // - signKey: 32 bytes for HmacSHA256
            val sessionKey = try {
                config.property("encryption.session-key").getString()
            } catch (e: Exception) {
                "test-session-key-for-testing-only-48bytes!!!!!!!"
            }
            
            // Convert the session key to bytes
            val fullKeyBytes = sessionKey.toByteArray()
            
            // Derive encrypt key (first 16 bytes) for AES-128 (most compatible)
            val encryptKeyBytes = if (fullKeyBytes.size >= 16) {
                fullKeyBytes.copyOf(16)
            } else {
                // Pad with zeros if key is too short
                (fullKeyBytes + ByteArray(16 - fullKeyBytes.size) { 0x00 }).copyOf(16)
            }
            
            // Derive sign key (next 32 bytes) for HmacSHA256
            val signKeyBytes = if (fullKeyBytes.size >= 48) {
                fullKeyBytes.copyOfRange(16, 48)
            } else {
                // Pad with zeros if key is too short
                val padded = fullKeyBytes + ByteArray(48 - fullKeyBytes.size) { 0x00 }
                padded.copyOfRange(16, 48)
            }
            
            transform(SessionTransportTransformerEncrypt(
                encryptKeyBytes,
                signKeyBytes
            ))
        }
    }
    
    // Install CORS plugin for cross-origin requests (frontend on different port during dev)
    install(CORS) {
        anyHost()
        allowCredentials = true
    }
    
    install(Authentication) {
        // Session-based authentication for session management
        session<UserSession>("session-auth") {
            validate { session ->
                // Session validation - check if expired
                if (session.expiresAt < System.currentTimeMillis()) {
                    null // Session expired
                } else {
                    UserId(session.userId) // Valid session
                }
            }
        }
        
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
            
            // Generate a secure random state for CSRF protection
            val state = generateState()
            val scopeString = googleConfig.scopes.joinToString(" ")
            
            // Create JWT for the state - this allows validation without server-side storage
            val stateJwt = createOauthStateJwt(state, jwtSecretKey)
            
            val authUrl = buildString {
                append("https://accounts.google.com/o/oauth2/v2/auth")
                append("?client_id=${googleConfig.clientId}")
                append("&redirect_uri=${googleConfig.redirectUri}")
                append("&response_type=code")
                append("&scope=${scopeString}")
                append("&state=$stateJwt")  // Pass JWT instead of raw state
                append("&access_type=offline")
            }
            
            call.application.log.info("OAuth login - Generated state: $state, JWT: ${stateJwt.take(50)}...")
            
            call.respondRedirect(authUrl)
        }
        
        // Google OAuth callback handler
        get("/auth/google/callback") {
            val code = call.request.queryParameters["code"]
            val stateJwt = call.request.queryParameters["state"]  // JWT containing the state
            
            // Validate and extract state from JWT
            val storedState = if (stateJwt != null) {
                validateOauthStateJwt(stateJwt, jwtSecretKey)
            } else {
                null
            }
            
            // Log the received state for debugging
            call.application.log.info("OAuth callback - Received state JWT: ${if(stateJwt != null) "${stateJwt.take(30)}..." else "null"}, Valid state: ${if(storedState != null) "VALID" else "INVALID"}")
            
            // CSRF protection - validate state parameter
            if (storedState == null) {
                call.application.log.error("Invalid or missing state JWT")
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "error" to "Invalid state parameter",
                    "message" to "CSRF validation failed"
                ))
                return@get
            }
            
            // State validated successfully - the JWT was valid and not expired
            call.application.log.info("OAuth state validated successfully")
            
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
                    call.application.log.error("OAuth callback - Failed to exchange code for token")
                    call.respond(HttpStatusCode.Unauthorized, mapOf(
                        "error" to "Failed to verify Google authentication"
                    ))
                    return@get
                }
                call.application.log.info("OAuth callback - Got ID token: ${idToken.take(50)}...")
                
                // Verify the ID token
                val jsonFactory: JsonFactory = GsonFactory.getDefaultInstance()
                val verifier = GoogleIdTokenVerifier.Builder(NetHttpTransport(), jsonFactory)
                    .setAudience(listOf(googleConfig.clientId))
                    .build()
                
                call.application.log.info("OAuth callback - Verifying ID token...")
                val verifiedToken = verifier.verify(idToken)
                
                if (verifiedToken == null) {
                    call.application.log.error("OAuth callback - Invalid Google ID token")
                    call.respond(HttpStatusCode.Unauthorized, mapOf(
                        "error" to "Invalid Google ID token"
                    ))
                    return@get
                }
                call.application.log.info("OAuth callback - ID token verified successfully")
                
                // Extract user information from the token
                val payload = verifiedToken.payload
                val googleUserId = payload.subject
                val email = payload.get("email") as? String ?: ""
                val name = payload.get("name") as? String ?: ""
                val givenName = payload.get("given_name") as? String
                val familyName = payload.get("family_name") as? String
                val pictureUrl = payload.get("picture") as? String
                
                call.application.log.info("OAuth callback - Extracted user: email=$email, name=$name, googleUserId=$googleUserId")
                
                // Create or lookup user
                val user = if (userLookupService != null) {
                    call.application.log.info("OAuth callback - Using userLookupService to find/create user")
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
                    call.application.log.info("OAuth callback - Creating fallback user (no userLookupService)")
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
                
                // Persist user to database if userRepository is provided
                var finalUser = user
                if (userRepository != null) {
                    try {
                        val existingUser = userRepository.findByEmail(user.email.lowercase())
                        if (existingUser == null) {
                            // New user - persist to database
                            userRepository.create(user)
                            call.application.log.info("OAuth callback - Persisted new user to database: ${user.id}")
                            // Verify the user was actually persisted by looking it up
                            finalUser = userRepository.findById(user.id) ?: user
                        } else {
                            // Existing user - use the existing user ID
                            finalUser = existingUser
                            call.application.log.info("OAuth callback - User already exists in database: ${existingUser.id}")
                        }
                    } catch (e: Exception) {
                        call.application.log.error("Failed to persist user to database", e)
                        // Try to find existing user by email as fallback
                        val existingUser = try {
                            userRepository.findByEmail(user.email.lowercase())
                        } catch (e2: Exception) {
                            null
                        }
                        if (existingUser != null) {
                            finalUser = existingUser
                            call.application.log.info("OAuth callback - Using existing user: ${existingUser.id}")
                        } else {
                            call.application.log.error("Cannot proceed without user in database - login failed")
                            call.respond(HttpStatusCode.InternalServerError, mapOf(
                                "error" to "Authentication failed",
                                "message" to "Unable to create user account"
                            ))
                            return@get
                        }
                    }
                } else {
                    call.application.log.error("userRepository not available - cannot persist users, login failed")
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "error" to "Authentication failed",
                        "message" to "User database not configured"
                    ))
                    return@get
                }
                
                call.application.log.info("OAuth callback - Using user for session: ${finalUser.id}")
                
                // Create session and store it using Ktor Sessions with the verified user
                val sessionData = UserSession(
                    userId = finalUser.id.toString(),  // Convert UUID to String
                    email = finalUser.email,
                    oauthToken = idToken,
                    expiresAt = System.currentTimeMillis() + 86400000 // 24 hours
                )
                
                call.application.log.info("OAuth callback - Creating session data: userId=${sessionData.userId}, email=${sessionData.email}, expiresAt=${sessionData.expiresAt}")
                call.application.log.debug("OAuth callback - Session oauthToken length: ${sessionData.oauthToken.length} characters")
                
                // Set session using Ktor Sessions API
                call.sessions.set(sessionData)
                
                call.application.log.info("OAuth callback - Session set successfully for user: ${sessionData.userId}, redirecting to dashboard")
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
        
        // Logout endpoint - accessible without authentication (allows clearing session)
        post("/auth/logout") {
            // Clear session using Ktor Sessions API
            call.sessions.clear<UserSession>()
            
            call.respond(HttpStatusCode.OK, mapOf(
                "message" to "Logged out successfully"
            ))
        }
        
        // Get current user info (for frontend) - returns auth status without requiring authentication
        authenticate("session-auth", optional = true) {
            get("/api/v1/auth/me") {
                // DEBUG: Log when this endpoint is called
                call.application.log.info("=== /api/v1/auth/me endpoint called ===")
                
                val userId = call.principal<UserId>()?.id
                call.application.log.info("UserId from principal: $userId")
                
                // DEBUG: Log cookie headers received
                val allCookies = call.request.headers["Cookie"]
                
                if (userId == null) {
                    call.application.log.warn("UserId is null - returning 401")
                    // Return 401 with authenticated: false in the body
                    call.respond(HttpStatusCode.Unauthorized, mapOf(
                        "authenticated" to false,
                        "error" to "Not authenticated"
                    ))
                    return@get
                }
                
                // Get session data to include email in response
                val session = call.sessions.get<UserSession>()
                
                // Return user info without sensitive data (no oauthToken)
                call.respond(HttpStatusCode.OK, mapOf(
                    "authenticated" to true,
                    "userId" to userId,
                    "email" to (session?.email ?: ""),
                    "name" to "",  // Not stored in session - would need database lookup
                    "pictureUrl" to ""  // Not stored in session - would need database lookup
                ))
                call.application.log.info("=== /api/v1/auth/me endpoint completed ===")
            }
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

// Helper function to generate a secure random state for CSRF protection
private fun generateState(): String {
    val secureRandom = SecureRandom()
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

// Helper function to create a signed JWT for OAuth state
private fun createOauthStateJwt(state: String, secretKey: ByteArray): String {
    val now = Instant.now()
    
    // Build JWT payload
    val payload = buildString {
        append("{\"state\":\"$state\"")
        append(",\"iat\":${now.epochSecond}")
        append(",\"exp\":${now.plusSeconds(300).epochSecond}}")  // 5 minute expiry
    }
    
    // Build JWT header
    val header = Base64.getUrlEncoder().withoutPadding().encodeToString(
        "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".toByteArray()
    )
    
    // Encode payload (no padding for URL safety)
    val encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
    
    // Create signature
    val message = "$header.$encodedPayload"
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secretKey, "HmacSHA256"))
    val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(message.toByteArray()))
    
    return "$header.$encodedPayload.$signature"
}

// Helper function to validate OAuth state JWT
private fun validateOauthStateJwt(jwt: String, secretKey: ByteArray): String? {
    try {
        val parts = jwt.split(".")
        if (parts.size != 3) {
            println("JWT validation failed: invalid format, parts=${parts.size}")
            return null
        }
        
        val header = parts[0]
        val encodedPayload = parts[1]
        val signature = parts[2]
        
        // Verify signature
        val message = "$header.$encodedPayload"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey, "HmacSHA256"))
        val expectedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(message.toByteArray()))
        
        println("JWT signature check: received='${signature.take(20)}...', expected='${expectedSignature.take(20)}...'")
        println("Message to sign: $message")
        println("Secret key length: ${secretKey.size}")
        
        if (signature != expectedSignature) {
            println("JWT validation failed: signature mismatch")
            return null  // Signature invalid
        }
        
        // Decode payload
        val payloadJson = String(Base64.getUrlDecoder().decode(encodedPayload))
        println("Decoded JWT payload: $payloadJson")
        
        // Parse and validate
        val state = extractJsonValue(payloadJson, "state")
        println("Extracted state: $state")
        
        if (state == null) {
            println("JWT validation failed: could not extract state from payload")
            return null
        }
        
        val iat = extractJsonValue(payloadJson, "iat")?.toLongOrNull()
        val exp = extractJsonValue(payloadJson, "exp")?.toLongOrNull()
        
        println("Token iat=$iat, exp=$exp, current=${Instant.now().epochSecond}")
        
        // Check expiration
        if (exp != null && Instant.now().epochSecond > exp) {
            println("JWT validation failed: token expired")
            return null  // Token expired
        }
        
        println("JWT validated successfully, state=$state")
        return state
    } catch (e: Exception) {
        println("JWT validation exception: ${e.message}")
        e.printStackTrace()
        return null
    }
}

// Simple JSON value extractor (avoids external dependencies)
private fun extractJsonValue(json: String, key: String): String? {
    val pattern = "\"$key\":\"([^\"]+)\""
    val regex = Regex(pattern)
    return regex.find(json)?.groupValues?.get(1)
}

// Helper function to exchange authorization code for ID token
private fun exchangeCodeForIdToken(code: String, config: GoogleOAuthConfig): String? {
    try {
        println("Exchanging code for token:")
        println("  Client ID: ${config.clientId.take(20)}...")
        println("  Redirect URI: ${config.redirectUri}")
        println("  Code length: ${code.length}")
        
        // Use raw HTTP connection to exchange the code
        val url = URL("https://oauth2.googleapis.com/token")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        
        val body = buildString {
            append("code=$code&")
            append("client_id=${config.clientId}&")
            append("client_secret=${config.clientSecret}&")
            append("redirect_uri=${config.redirectUri}&")
            append("grant_type=authorization_code")
        }
        
        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(body)
            writer.flush()
        }
        
        val responseCode = conn.responseCode
        val responseBody = if (responseCode == HttpURLConnection.HTTP_OK) {
            conn.inputStream.bufferedReader().readText()
        } else {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        
        println("Response code: $responseCode")
        println("Response body: $responseBody")
        
        if (responseCode == HttpURLConnection.HTTP_OK) {
            // Parse JSON response to extract id_token - handle multi-line JSON
            val idTokenRegex = Regex("\"id_token\"\\s*:\\s*\"([^\"]+)\"")
            val idTokenMatch = idTokenRegex.find(responseBody)
            if (idTokenMatch != null) {
                val idToken = idTokenMatch.groupValues[1]
                println("Token exchange successful, got ID token: ${idToken.take(30)}...")
                return idToken
            } else {
                println("Token exchange failed: could not extract id_token from response")
                // Try alternative parsing - look for the key without escaping
                val altRegex = Regex("""id_token":\s*"([^"]+)""")
                val altMatch = altRegex.find(responseBody)
                if (altMatch != null) {
                    val idToken = altMatch.groupValues[1]
                    println("Token exchange successful (alt parse), got ID token: ${idToken.take(30)}...")
                    return idToken
                }
            }
        }
        
        return null
    } catch (e: Exception) {
        println("Error exchanging code for token: ${e.message}")
        e.printStackTrace()
        return null
    }
}

// Helper function to exchange refresh token for access token
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

// Helper function to generate encryption salt
private fun generateEncryptionSalt(): String {
    val secureRandom = SecureRandom()
    val bytes = ByteArray(16)
    secureRandom.nextBytes(bytes)
    return Base64.getEncoder().encodeToString(bytes)
}
