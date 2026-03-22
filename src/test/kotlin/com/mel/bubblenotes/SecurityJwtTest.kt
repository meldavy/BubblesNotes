package com.mel.bubblenotes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for OAuth state JWT functions (TASK-008).
 *
 * Tests cover:
 * - JWT creation with valid state
 * - JWT validation with valid tokens
 * - JWT validation rejects expired tokens
 * - JWT validation rejects tampered signatures
 * - JWT validation rejects malformed tokens
 */
class SecurityJwtTest {
    private val testSecretKey = "test-secret-key-for-jwt-signing-32bytes!!!!".toByteArray()
    private val validState = "test-state-12345"

    @Test
    fun `createOauthStateJwt should create valid JWT with correct claims`() {
        // Act
        val jwt = createOauthStateJwt(validState, testSecretKey)

        // Assert - JWT should be non-empty and decodable
        assertNotNull(jwt)
        assertTrue(jwt.isNotEmpty())

        // Verify the JWT can be decoded and contains correct claims
        val algorithm = Algorithm.HMAC256(testSecretKey)
        val verifier = JWT.require(algorithm).build()
        val decodedJWT = verifier.verify(jwt)

        assertEquals(validState, decodedJWT.getClaim("state").asString())
        assertNotNull(decodedJWT.issuedAt)
        assertNotNull(decodedJWT.expiresAt)

        // Verify expiration is approximately 5 minutes from issuance
        val issuedAt = decodedJWT.issuedAt.toInstant()
        val expiresAt = decodedJWT.expiresAt.toInstant()
        val duration = ChronoUnit.SECONDS.between(issuedAt, expiresAt)
        assertEquals(300, duration, "JWT should expire in 300 seconds (5 minutes)")
    }

    @Test
    fun `validateOauthStateJwt should extract state from valid JWT`() {
        // Arrange
        val jwt = createOauthStateJwt(validState, testSecretKey)

        // Act
        val extractedState = validateOauthStateJwt(jwt, testSecretKey)

        // Assert
        assertEquals(validState, extractedState)
    }

    @Test
    fun `validateOauthStateJwt should return null for expired JWT`() {
        // Arrange - create an expired JWT manually
        val algorithm = Algorithm.HMAC256(testSecretKey)
        val expiredJwt =
            JWT.create()
                .withClaim("state", validState)
                .withIssuedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .withExpiresAt(Instant.now().minus(30, ChronoUnit.MINUTES))
                .sign(algorithm)

        // Act
        val extractedState = validateOauthStateJwt(expiredJwt, testSecretKey)

        // Assert
        assertNull(extractedState, "Expired JWT should return null")
    }

    @Test
    fun `validateOauthStateJwt should return null for tampered signature`() {
        // Arrange - create a valid JWT then tamper with it
        val validJwt = createOauthStateJwt(validState, testSecretKey)
        val parts = validJwt.split(".")
        require(parts.size == 3)

        // Tamper with the payload
        val tamperedPayload =
            java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"state\":\"tampered-state\"}".toByteArray())
        val tamperedJwt = "${parts[0]}.$tamperedPayload.${parts[2]}"

        // Act
        val extractedState = validateOauthStateJwt(tamperedJwt, testSecretKey)

        // Assert
        assertNull(extractedState, "Tampered JWT should return null")
    }

    @Test
    fun `validateOauthStateJwt should return null for wrong secret key`() {
        // Arrange
        val jwt = createOauthStateJwt(validState, testSecretKey)
        val wrongSecretKey = "wrong-secret-key-for-jwt-signing-32bytes!!!!".toByteArray()

        // Act
        val extractedState = validateOauthStateJwt(jwt, wrongSecretKey)

        // Assert
        assertNull(extractedState, "JWT signed with different key should return null")
    }

    @Test
    fun `validateOauthStateJwt should return null for malformed JWT`() {
        // Arrange
        val malformedJwt = "not.a.valid.jwt.token"

        // Act
        val extractedState = validateOauthStateJwt(malformedJwt, testSecretKey)

        // Assert
        assertNull(extractedState, "Malformed JWT should return null")
    }

    @Test
    fun `validateOauthStateJwt should return null for empty JWT string`() {
        // Arrange
        val emptyJwt = ""

        // Act
        val extractedState = validateOauthStateJwt(emptyJwt, testSecretKey)

        // Assert
        assertNull(extractedState, "Empty JWT should return null")
    }

    @Test
    fun `validateOauthStateJwt should return null for JWT with missing state claim`() {
        // Arrange - create JWT without state claim
        val algorithm = Algorithm.HMAC256(testSecretKey)
        val jwtWithoutState =
            JWT.create()
                .withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .sign(algorithm)

        // Act
        val extractedState = validateOauthStateJwt(jwtWithoutState, testSecretKey)

        // Assert
        assertNull(extractedState, "JWT without state claim should return null")
    }

    @Test
    fun `createOauthStateJwt should create unique tokens for different states`() {
        // Arrange
        val state1 = "state-1"
        val state2 = "state-2"

        // Act
        val jwt1 = createOauthStateJwt(state1, testSecretKey)
        val jwt2 = createOauthStateJwt(state2, testSecretKey)

        // Assert
        assert(jwt1 != jwt2) { "Different states should produce different JWTs" }

        // Verify each JWT contains its respective state
        assertEquals(state1, validateOauthStateJwt(jwt1, testSecretKey))
        assertEquals(state2, validateOauthStateJwt(jwt2, testSecretKey))
    }

    @Test
    fun `createOauthStateJwt should handle special characters in state`() {
        // Arrange
        val specialState = "state-with-special-chars-!@#$%^&*()_+-=[]{}|;':\",./<>?"

        // Act
        val jwt = createOauthStateJwt(specialState, testSecretKey)

        // Assert
        assertEquals(specialState, validateOauthStateJwt(jwt, testSecretKey))
    }

    @Test
    fun `createOauthStateJwt should handle empty state string`() {
        // Arrange
        val emptyState = ""

        // Act
        val jwt = createOauthStateJwt(emptyState, testSecretKey)

        // Assert
        assertEquals(emptyState, validateOauthStateJwt(jwt, testSecretKey))
    }
}
