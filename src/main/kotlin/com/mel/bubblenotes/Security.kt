package com.mel.bubblenotes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.mel.bubblenotes.models.User
import com.mel.bubblenotes.repositories.RefreshTokenRepository
import com.mel.bubblenotes.repositories.UserRepository
import com.mel.bubblenotes.services.EncryptionService
import com.mel.bubblenotes.services.JWTTokenService
import io.ktor.http.*
import io.ktor.http.cookies
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

// Security headers plugin - adds standard security headers to all responses
private val SecurityHeadersPlugin =
    createApplicationPlugin(name = "SecurityHeaders") {
        onCall { call ->
            // X-Content-Type-Options: Prevents MIME type sniffing (always applied)
            call.response.headers.append("X-Content-Type-Options", "nosniff")

            // X-Frame-Options: Prevents clickjacking attacks (always applied)
            call.response.headers.append("X-Frame-Options", "DENY")
        }

        onCallRespond { call, _ ->
            // Strict-Transport-Security: Only in production environment
            val isProduction =
                call.application.environment.config
                    .propertyOrNull("ktor.deployment.environment")?.getString() == "production"

            if (isProduction) {
                call.response.headers.append(
                    "Strict-Transport-Security",
                    "max-age=31536000; includeSubDomains",
                )
            }
        }
    }

fun Application.installSecurityHeaders() {
    install(SecurityHeadersPlugin)
}

data class UserId(val id: String)

// JWT principal for authentication
data class JWTPrincipal(
    val userId: UUID,
    val email: String,
) : Principal

// JWT authentication plugin for token-based authentication
fun Application.configureJWTAuthentication(
    jwtTokenService: JWTTokenService,
    userRepository: UserRepository,
) {
    val secretKey = getJWTSecretKey()

    install(Authentication) {
        // JWT-based authentication for API routes
        jwt("jwt-auth") {
            verifier(
                JWT.require(Algorithm.HMAC256(secretKey))
                    .build(),
            )
            // Custom token extraction: check Authorization header first, then access_token cookie
            authHeader { call ->
                // First try Authorization header
                val authHeader = call.request.headers["Authorization"]
                var token = authHeader?.substringAfter("Bearer ", "")?.trim()

                // If no header, try access_token cookie
                if (token.isNullOrBlank()) {
                    token = call.request.cookies["access_token"]
                }

                // Return HttpAuthHeader if token found, null otherwise
                return@authHeader token?.takeIf { it.isNotBlank() }?.let {
                    io.ktor.http.auth.HttpAuthHeader.Single("Bearer", it)
                }
            }
            validate { credentials ->
                // Extract user ID from JWT subject
                val userIdString = credentials.payload.subject
                val userId =
                    try {
                        UUID.fromString(userIdString)
                    } catch (e: Exception) {
                        return@validate null
                    }

                // Look up user in database
                val user = userRepository.findById(userId)
                if (user != null) {
                    JWTPrincipal(userId, user.email)
                } else {
                    null
                }
            }
        }
    }
}

// Helper function to get JWT secret key from configuration
fun Application.getJWTSecretKey(): ByteArray {
    val config = environment.config
    return try {
        config.property("jwt.secret-key").getString().toByteArray()
    } catch (e: Exception) {
        environment.log.warn(
            "JWT secret key not configured, using default test value. DO NOT use in production!",
        )
        "default-test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()
    }
}

// User lookup service interface for OAuth user management
interface UserLookupService {
    fun findOrCreateUser(
        googleUserId: String,
        email: String,
        name: String,
        givenName: String?,
        familyName: String?,
        pictureUrl: String?,
        oauthToken: String,
    ): User
}

// Session data for encrypted storage - must be serializable for Ktor sessions
@kotlinx.serialization.Serializable
data class UserSession(
    // Changed from UUID to String for serialization
    val userId: String,
    val email: String,
    val oauthToken: String,
    val expiresAt: Long,
)

// Google OAuth configuration
class GoogleOAuthConfig(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val scopes: List<String> = listOf("email", "profile"),
)

fun Application.configureSecurity(
    userLookupService: UserLookupService? = null,
    encryptionService: EncryptionService? = null,
) {
    // Install security headers middleware (TASK-013)
    installSecurityHeaders()

    // Retrieve userRepository from Guice DI if available
    val userRepository =
        try {
            getInjector().getInstance(UserRepository::class.java)
        } catch (e: Exception) {
            environment.log.warn("UserRepository not available in DI container, user persistence disabled")
            null
        }

    // Retrieve refreshTokenRepository from Guice DI if available
    val refreshTokenRepository =
        try {
            getInjector().getInstance(RefreshTokenRepository::class.java)
        } catch (e: Exception) {
            environment.log.warn("RefreshTokenRepository not available in DI container, JWT authentication disabled")
            null
        }

    // Retrieve JWTTokenService from Guice DI if available
    val jwtTokenService =
        try {
            getInjector().getInstance(JWTTokenService::class.java)
        } catch (e: Exception) {
            environment.log.warn("JWTTokenService not available in DI container, JWT authentication disabled")
            null
        }

    val config = environment.config

    // Read Google OAuth configuration from application.yaml with fallback defaults
    val googleClientId =
        try {
            config.property("oauth.google.client-id").getString()
        } catch (e: Exception) {
            environment.log.warn("OAuth client-id not configured, using empty string")
            ""
        }
    val googleClientSecret =
        try {
            config.property("oauth.google.client-secret").getString()
        } catch (e: Exception) {
            environment.log.warn("OAuth client-secret not configured, using empty string")
            ""
        }
    val googleRedirectUri =
        try {
            config.property("oauth.google.redirect-uri").getString()
        } catch (e: Exception) {
            environment.log.warn("OAuth redirect-uri not configured, using default")
            "http://localhost:8080/auth/google/callback"
        }
    val googleScopes =
        try {
            config.property("oauth.google.scopes").getString()
                .split(",").map { it.trim() }
        } catch (e: Exception) {
            environment.log.warn("OAuth scopes not configured, using default")
            listOf("email", "profile")
        }

    val googleConfig =
        GoogleOAuthConfig(
            clientId = googleClientId,
            clientSecret = googleClientSecret,
            redirectUri = googleRedirectUri,
            scopes = googleScopes,
        )

    // Get encryption key for JWT signing - uses fallback defaults if not configured
    val jwtSecretKey: ByteArray =
        try {
            val key = config.property("jwt.secret-key").getString()
            if (key.isBlank()) {
                environment.log.warn(
                    "JWT secret key is empty or not configured, using default test value. DO NOT use in production!",
                )
                // Fallback default for testing/development - MUST be at least 32 bytes
                "default-test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()
            } else {
                key.toByteArray()
            }
        } catch (e: Exception) {
            environment.log.warn(
                "JWT secret key not configured, using default test value. DO NOT use in production!",
            )
            // Fallback default for testing/development - MUST be at least 32 bytes
            "default-test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()
        }

    // Configure JWT authentication if all dependencies are available
    if (userRepository != null && refreshTokenRepository != null && jwtTokenService != null) {
        configureJWTAuthentication(jwtTokenService, userRepository)
        environment.log.info("JWT authentication configured successfully")
    } else {
        environment.log.warn(
            "JWT authentication not configured - missing dependencies (userRepository: ${userRepository != null}, refreshTokenRepository: ${refreshTokenRepository != null}, jwtTokenService: ${jwtTokenService != null})",
        )
    }

    // Install CORS plugin for cross-origin requests
    install(CORS) {
        // IMPORTANT: when allowCredentials = true, avoid anyHost().
        // Read allowed origin from configuration (env var ORIGIN or application.yaml `origin`).
        val originValue =
            try {
                config.property("origin").getString().trim()
            } catch (e: Exception) {
                this@configureSecurity.environment.log.warn("Origin not configured, using default for testing")
                "http://localhost:8080"
            }

        if (originValue.isBlank()) {
            this@configureSecurity.environment.log.warn("CORS ORIGIN is blank, using default for testing")
        }

        try {
            val uri = java.net.URI(originValue)
            val scheme = uri.scheme ?: throw IllegalArgumentException("ORIGIN must include scheme (http/https)")
            // Prefer host; if absent (edge cases), try authority
            val hostRaw = uri.host ?: uri.authority ?: throw IllegalArgumentException("ORIGIN must include host")
            val port = uri.port
            val hostForKtor =
                if (port == -1 || (scheme == "http" && port == 80) || (scheme == "https" && port == 443)) {
                    hostRaw
                } else {
                    "$hostRaw:$port"
                }

            allowHost(hostForKtor, schemes = listOf(scheme))
            this@configureSecurity.environment.log.info("Configured CORS allowed origin: $scheme://$hostForKtor")
        } catch (e: Exception) {
            this@configureSecurity.environment.log.error("Invalid ORIGIN configuration '$originValue': ${e.message}")
            throw e
        }

        allowCredentials = true

        // Allow the HTTP methods used by the frontend
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)

        // Allow JSON and other non-simple content types in requests
        allowHeader(HttpHeaders.ContentType)
        // If custom headers are used (e.g., Authorization/X-Requested-With), add them here:
        // allowHeader(HttpHeaders.Authorization)
        // allowHeader("X-Requested-With")

        // Be explicit that non-simple content types are permitted (helps with preflight)
        allowNonSimpleContentTypes = true
    }

    routing {
        // Google OAuth login endpoint
        get("/auth/google") {
            if (googleConfig.clientId.isEmpty()) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    mapOf(
                        "error" to "Google OAuth not configured",
                        "message" to "Please set GOOGLE_CLIENT_ID environment variable",
                    ),
                )
                return@get
            }

            // Generate a secure random state for CSRF protection
            val state = generateState()
            val scopeString = googleConfig.scopes.joinToString(" ")

            // Create JWT for the state - this allows validation without server-side storage
            val stateJwt = createOauthStateJwt(state, jwtSecretKey)

            val authUrl =
                buildString {
                    append("https://accounts.google.com/o/oauth2/v2/auth")
                    append("?client_id=${googleConfig.clientId}")
                    append("&redirect_uri=${googleConfig.redirectUri}")
                    append("&response_type=code")
                    append("&scope=$scopeString")
                    append("&state=$stateJwt") // Pass JWT instead of raw state
                    append("&access_type=offline")
                }

            call.application.log.info("OAuth login - Generated state: $state, JWT: ${stateJwt.take(50)}...")

            call.respondRedirect(authUrl)
        }

        // Google OAuth callback handler
        get("/auth/google/callback") {
            val code = call.request.queryParameters["code"]
            val stateJwt = call.request.queryParameters["state"] // JWT containing the state

            // Validate and extract state from JWT
            val storedState =
                if (stateJwt != null) {
                    validateOauthStateJwt(stateJwt, jwtSecretKey)
                } else {
                    null
                }

            // Log the received state for debugging
            call.application.log.info(
                "OAuth callback - Received state JWT: ${if (stateJwt != null) {
                    "${stateJwt.take(
                        30,
                    )}..."
                } else {
                    "null"
                }}, Valid state: ${if (storedState != null) "VALID" else "INVALID"}",
            )

            // CSRF protection - validate state parameter
            if (storedState == null) {
                call.application.log.error("Invalid or missing state JWT")
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf(
                        "error" to "Invalid state parameter",
                        "message" to "CSRF validation failed",
                    ),
                )
                return@get
            }

            // State validated successfully - the JWT was valid and not expired
            call.application.log.info("OAuth state validated successfully")

            if (code.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf(
                        "error" to "Authorization code not provided",
                    ),
                )
                return@get
            }

            try {
                // Exchange authorization code for ID token and refresh token
                val tokenResponse = exchangeCodeForIdToken(code, googleConfig)

                if (tokenResponse == null) {
                    call.application.log.error("OAuth callback - Failed to exchange code for token")
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf(
                            "error" to "Failed to verify Google authentication",
                        ),
                    )
                    return@get
                }
                val idToken = tokenResponse.idToken
                call.application.log.info("OAuth callback - Got ID token: ${idToken.take(50)}...")

                // Verify the ID token
                val jsonFactory: JsonFactory = GsonFactory.getDefaultInstance()
                val verifier =
                    GoogleIdTokenVerifier.Builder(NetHttpTransport(), jsonFactory)
                        .setAudience(listOf(googleConfig.clientId))
                        .build()

                call.application.log.info("OAuth callback - Verifying ID token...")
                val verifiedToken = verifier.verify(idToken)

                if (verifiedToken == null) {
                    call.application.log.error("OAuth callback - Invalid Google ID token")
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf(
                            "error" to "Invalid Google ID token",
                        ),
                    )
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
                val user =
                    if (userLookupService != null) {
                        call.application.log.info("OAuth callback - Using userLookupService to find/create user")
                        userLookupService.findOrCreateUser(
                            googleUserId = googleUserId,
                            email = email,
                            name = name,
                            givenName = givenName,
                            familyName = familyName,
                            pictureUrl = pictureUrl,
                            oauthToken = idToken,
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
                            apiKey = UUID.randomUUID().toString(),
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
                        val existingUser =
                            try {
                                userRepository.findByEmail(user.email.lowercase())
                            } catch (e2: Exception) {
                                null
                            }
                        if (existingUser != null) {
                            finalUser = existingUser
                            call.application.log.info("OAuth callback - Using existing user: ${existingUser.id}")
                        } else {
                            call.application.log.error("Cannot proceed without user in database - login failed")
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf(
                                    "error" to "Authentication failed",
                                    "message" to "Unable to create user account",
                                ),
                            )
                            return@get
                        }
                    }
                } else {
                    call.application.log.error("userRepository not available - cannot persist users, login failed")
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf(
                            "error" to "Authentication failed",
                            "message" to "User database not configured",
                        ),
                    )
                    return@get
                }

                call.application.log.info("OAuth callback - Using user for session: ${finalUser.id}")

                // Generate JWT token pair for the user
                val tokenPair =
                    if (jwtTokenService != null && refreshTokenRepository != null) {
                        jwtTokenService.createTokenPair(finalUser.id)
                    } else {
                        environment.log.error("JWT token service not available - cannot issue tokens")
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf(
                                "error" to "Authentication failed",
                                "message" to "JWT service not configured",
                            ),
                        )
                        return@get
                    }

                call.application.log.info(
                    "OAuth callback - JWT tokens generated for user: ${finalUser.id}, redirecting to dashboard",
                )

                // Store refresh token in HttpOnly, Secure cookie
                val isProduction = config.propertyOrNull("ktor.deployment.environment")?.getString() == "production"
                val sameSiteValue = if (isProduction) "None" else "Lax"
                val refreshCookie =
                    Cookie(
                        name = "refresh_token",
                        value = tokenPair.refreshToken,
                        path = "/",
                        httpOnly = true,
                        secure = isProduction,
                        maxAge = (tokenPair.refreshTokenExpiresAt / 1000).toInt(),
                        extensions = mapOf("SameSite" to sameSiteValue),
                    )
                call.response.cookies.append(refreshCookie)

                // Store access token in a cookie for browser-native requests (e.g., <img> tags)
                // This cookie is NOT HttpOnly so JavaScript can still access it, but it will be
                // automatically sent with all requests including image loads
                val accessCookie =
                    Cookie(
                        name = "access_token",
                        value = tokenPair.accessToken,
                        path = "/",
                        // Not HttpOnly so JS can still read it if needed
                        httpOnly = false,
                        secure = isProduction,
                        maxAge = (tokenPair.accessTokenExpiresAt / 1000).toInt(),
                        extensions = mapOf("SameSite" to sameSiteValue),
                    )
                call.response.cookies.append(accessCookie)

                call.application.log.info("OAuth callback - Refresh token stored securely")

                call.application.log.info(
                    "OAuth callback - JWT tokens set successfully for user: ${finalUser.id}, redirecting to dashboard",
                )
                // Redirect to dashboard or original URL, including access token in URL fragment for frontend
                val redirectUrl = call.request.queryParameters["redirect"] ?: "/dashboard"
                // Append access token as URL fragment (not sent to server, only accessible via JavaScript)
                val finalRedirectUrl =
                    if (redirectUrl.contains("#")) {
                        "$redirectUrl&accessToken=${tokenPair.accessToken}#authenticated"
                    } else {
                        "$redirectUrl#accessToken=${tokenPair.accessToken}"
                    }
                call.respondRedirect(finalRedirectUrl)
            } catch (e: Exception) {
                call.application.log.error("OAuth callback error", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "error" to "Authentication failed",
                        "message" to e.message,
                    ),
                )
            }
        }

        // Logout endpoint - accessible without authentication (allows clearing session)
        post("/auth/logout") {
            // Revoke refresh token if available
            val refreshToken = call.request.cookies["refresh_token"]
            if (!refreshToken.isNullOrBlank() && jwtTokenService != null) {
                jwtTokenService.revokeRefreshToken(refreshToken)
                call.application.log.info("Logout - Refresh token revoked")
            }

            // Clear refresh token cookie
            call.response.cookies.append(
                Cookie(
                    name = "refresh_token",
                    value = "",
                    path = "/",
                    maxAge = 0,
                ),
            )

            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "message" to "Logged out successfully",
                ),
            )
        }

        // Get current user info (for frontend) - returns auth status without requiring authentication
        get("/api/v1/auth/me") {
            val authHeader = call.request.headers["Authorization"]
            val accessToken = authHeader?.removePrefix("Bearer ")

            if (accessToken.isNullOrBlank() || jwtTokenService == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf(
                        "authenticated" to false,
                        "error" to "Not authenticated",
                    ),
                )
                return@get
            }

            val validation = jwtTokenService.validateAccessToken(accessToken)
            if (!validation.isValid || validation.userId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf(
                        "authenticated" to false,
                        "error" to "Invalid or expired token",
                    ),
                )
                return@get
            }

            // Look up user in database
            val user = userRepository?.findById(validation.userId)
            if (user == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf(
                        "authenticated" to false,
                        "error" to "User not found",
                    ),
                )
                return@get
            }

            @Suppress("UNCHECKED_CAST")
            call.respond(
                HttpStatusCode.OK,
                mapOf<String, Any>(
                    "authenticated" to true,
                    "userId" to user.id.toString(),
                    "email" to user.email,
                    "name" to (user.name ?: ""),
                    "pictureUrl" to (user.pictureUrl ?: ""),
                ) as Map<String, Any>,
            )
        }

        // Token refresh endpoint - exchanges refresh token for new access token with rotation
        post("/auth/refresh") {
            val refreshToken = call.request.cookies["refresh_token"]

            if (refreshToken.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf(
                        "error" to "No refresh token available",
                        "message" to "Please sign in again",
                    ),
                )
                return@post
            }

            if (jwtTokenService == null) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "error" to "Token refresh failed",
                        "message" to "JWT service not configured",
                    ),
                )
                return@post
            }

            try {
                // Rotate refresh token and get new token pair
                val newTokenPair = jwtTokenService.rotateRefreshToken(refreshToken)

                if (newTokenPair == null) {
                    // Token rotation failed - token may be expired, revoked, or stolen
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf(
                            "error" to "Failed to refresh token",
                            "message" to "Please sign in again",
                        ),
                    )
                    return@post
                }

                // Set new refresh token in cookie
                val isProduction = config.propertyOrNull("ktor.deployment.environment")?.getString() == "production"
                val sameSiteValue = if (isProduction) "None" else "Lax"
                val cookie =
                    Cookie(
                        name = "refresh_token",
                        value = newTokenPair.refreshToken,
                        path = "/",
                        httpOnly = true,
                        secure = isProduction,
                        maxAge = (newTokenPair.refreshTokenExpiresAt / 1000).toInt(),
                        extensions = mapOf("SameSite" to sameSiteValue),
                    )
                call.response.cookies.append(cookie)

                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "success" to true,
                        "accessToken" to newTokenPair.accessToken,
                        "accessTokenExpiresAt" to newTokenPair.accessTokenExpiresAt,
                    ),
                )
            } catch (e: Exception) {
                call.application.log.error("Token refresh error", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "error" to "Token refresh failed",
                        "message" to e.message,
                    ),
                )
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

// Helper function to create a signed JWT for OAuth state using Auth0 java-jwt library
internal fun createOauthStateJwt(
    state: String,
    secretKey: ByteArray,
): String {
    val now = Instant.now()

    // Use Auth0 java-jwt library for robust token creation
    val algorithm = Algorithm.HMAC256(secretKey)

    return JWT.create()
        .withClaim("state", state)
        .withIssuedAt(now)
        .withExpiresAt(now.plusSeconds(300)) // 5 minute expiry
        .sign(algorithm)
}

// Helper function to validate OAuth state JWT using Auth0 java-jwt library
internal fun validateOauthStateJwt(
    jwt: String,
    secretKey: ByteArray,
): String? {
    return try {
        val algorithm = Algorithm.HMAC256(secretKey)
        val verifier = JWT.require(algorithm).build()
        val decodedJWT = verifier.verify(jwt)

        // Extract the state claim
        decodedJWT.getClaim("state")?.asString()
    } catch (e: Exception) {
        // JWT validation failed - signature invalid, token expired, or malformed
        null
    }
}

// Helper function to extract JSON value (no longer used but kept for backward compatibility if needed elsewhere)
@Deprecated("Use Auth0 java-jwt library instead of manual JSON parsing")
private fun extractJsonValue(
    json: String,
    key: String,
): String? {
    val pattern = "\"$key\":\"([^\"]+)\""
    val regex = Regex(pattern)
    return regex.find(json)?.groupValues?.get(1)
}

// Helper function to exchange authorization code for ID token and refresh token
private data class TokenResponse(
    val idToken: String,
    val refreshToken: String?,
)

private fun exchangeCodeForIdToken(
    code: String,
    config: GoogleOAuthConfig,
): TokenResponse? {
    try {
        // Use raw HTTP connection to exchange the code
        val url = URL("https://oauth2.googleapis.com/token")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

        val body =
            buildString {
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
        val responseBody =
            if (responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }

        if (responseCode == HttpURLConnection.HTTP_OK) {
            // Parse JSON response to extract id_token and refresh_token - handle multi-line JSON
            val idTokenRegex = Regex("\"id_token\"\\s*:\\s*\"([^\"]+)\"")
            val idTokenMatch = idTokenRegex.find(responseBody)
            if (idTokenMatch != null) {
                val idToken = idTokenMatch.groupValues[1]
                // Extract refresh token if present (only returned on first authorization or when offline access requested)
                val refreshTokenRegex = Regex("\"refresh_token\"\\s*:\\s*\"([^\"]+)\"")
                val refreshTokenMatch = refreshTokenRegex.find(responseBody)
                val refreshToken = refreshTokenMatch?.groupValues?.get(1)
                return TokenResponse(idToken, refreshToken)
            } else {
                // Try alternative parsing - look for the key without escaping
                val altRegex = Regex("""id_token":\s*"([^"]+)""")
                val altMatch = altRegex.find(responseBody)
                if (altMatch != null) {
                    val idToken = altMatch.groupValues[1]
                    // Extract refresh token if present
                    val refreshTokenRegex = Regex("\"refresh_token\"\\s*:\\s*\"([^\"]+)\"")
                    val refreshTokenMatch = refreshTokenRegex.find(responseBody)
                    val refreshToken = refreshTokenMatch?.groupValues?.get(1)
                    return TokenResponse(idToken, refreshToken)
                }
            }
        }

        return null
    } catch (e: Exception) {
        println("Error exchanging code for token: ${e.message}")
        return null
    }
}

// Helper function to exchange refresh token for access token
private fun exchangeRefreshTokenForAccessToken(
    refreshToken: String,
    config: GoogleOAuthConfig,
): String? {
    try {
        val tokenRequest =
            GoogleRefreshTokenRequest(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                refreshToken,
                config.clientId,
                config.clientSecret,
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
