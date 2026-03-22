package com.mel.bubblenotes.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.mel.bubblenotes.repositories.RefreshTokenRepository
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Service for JWT token generation, validation, and refresh token rotation.
 * Follows testability constitution: class is open for mocking in tests.
 *
 * Tenets:
 * 1. Scalability: Access tokens are stateless (no DB lookup for validation)
 * 2. Distributed: Refresh token rotation is atomic (database transactions)
 * 3. Security: Token theft detection, rotation, and revocation support
 */
open class JWTTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val secretKey: ByteArray,
    private val accessTokenTtlSeconds: Long,
    private val refreshTokenTtlSeconds: Long,
) {
    companion object {
        private const val TOKEN_TYPE_ACCESS = "access"
        private const val TOKEN_TYPE_REFRESH = "refresh"
        private val secureRandom = SecureRandom()
    }

    /**
     * Generated refresh token data class.
     */
    data class TokenPair(
        val accessToken: String,
        val refreshToken: String,
        val accessTokenExpiresAt: Long,
        val refreshTokenExpiresAt: Long,
    )

    /**
     * Token validation result.
     */
    data class TokenValidationResult(
        val isValid: Boolean,
        val userId: UUID?,
        val error: String?,
        val theftDetected: Boolean = false,
    )

    /**
     * Generate a new refresh token string.
     */
    fun generateRefreshTokenString(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Create a new token pair (access + refresh) for a user.
     * Stores the refresh token in the database.
     *
     * @param userId The user ID
     * @param parentTokenHash Optional parent token hash for rotation
     * @return Token pair with access and refresh tokens
     */
    fun createTokenPair(
        userId: UUID,
        parentTokenHash: String? = null,
    ): TokenPair {
        val now = Instant.now()
        val accessTokenExpiresAt = now.plusSeconds(accessTokenTtlSeconds)
        val refreshTokenExpiresAt = now.plusSeconds(refreshTokenTtlSeconds)

        // Generate access token
        val accessToken =
            JWT.create()
                .withSubject(userId.toString())
                .withClaim("type", TOKEN_TYPE_ACCESS)
                .withIssuedAt(now)
                .withExpiresAt(accessTokenExpiresAt)
                .withClaim("userId", userId.toString())
                .sign(Algorithm.HMAC256(secretKey))

        // Generate refresh token string
        val refreshTokenString = generateRefreshTokenString()
        val refreshTokenHash = RefreshTokenRepository.hashToken(refreshTokenString)

        // Store refresh token in database
        refreshTokenRepository.create(
            userId = userId,
            token = refreshTokenString,
            expiresAt = refreshTokenExpiresAt.toEpochMilli(),
            parentTokenHash = parentTokenHash,
        )

        return TokenPair(
            accessToken = accessToken,
            refreshToken = refreshTokenString,
            accessTokenExpiresAt = accessTokenExpiresAt.toEpochMilli(),
            refreshTokenExpiresAt = refreshTokenExpiresAt.toEpochMilli(),
        )
    }

    /**
     * Validate an access token.
     * This is stateless - no database lookup required.
     *
     * @param accessToken The JWT access token
     * @return Token validation result
     */
    fun validateAccessToken(accessToken: String): TokenValidationResult {
        return try {
            val algorithm = Algorithm.HMAC256(secretKey)
            val verifier = JWT.require(algorithm).build()
            val decodedJWT = verifier.verify(accessToken)

            // Check token type
            if (decodedJWT.getClaim("type").asString() != TOKEN_TYPE_ACCESS) {
                return TokenValidationResult(
                    isValid = false,
                    userId = null,
                    error = "Invalid token type",
                )
            }

            val userId = UUID.fromString(decodedJWT.subject)
            TokenValidationResult(
                isValid = true,
                userId = userId,
                error = null,
            )
        } catch (e: Exception) {
            TokenValidationResult(
                isValid = false,
                userId = null,
                error = "Token validation failed: ${e.message}",
            )
        }
    }

    /**
     * Validate and rotate a refresh token.
     * This is stateful - requires database lookup and rotation.
     *
     * @param refreshTokenString The refresh token string
     * @return Token pair with new access and refresh tokens, or null if invalid
     */
    fun rotateRefreshToken(refreshTokenString: String): TokenPair? {
        val refreshTokenHash = RefreshTokenRepository.hashToken(refreshTokenString)

        // Look up the refresh token in database
        val refreshToken =
            refreshTokenRepository.findByHash(refreshTokenHash)
                ?: return null

        // Check if token is expired
        if (refreshToken.expiresAt < System.currentTimeMillis()) {
            return null
        }

        // Check if token is revoked
        if (refreshToken.revokedAt != null) {
            return null
        }

        // Check if token is already used (rotation)
        if (refreshToken.usedAt != null) {
            // Token was already used - potential theft
            if (refreshToken.parentTokenHash != null) {
                // This is a child token - check if parent was used
                val theftDetected = refreshTokenRepository.detectTheft(refreshTokenHash)
                if (theftDetected) {
                    // Revoke entire token chain
                    refreshTokenRepository.revokeTokenChain(refreshTokenHash, refreshToken.userId)
                }
            }
            return null
        }

        // Rotate the token: mark old as used, create new with parent reference
        // The rotate method now generates the new token internally and returns (hash, token)
        val newExpiresAt = Instant.now().plusSeconds(refreshTokenTtlSeconds)
        val (newRefreshTokenHash, newRefreshTokenString) = refreshTokenRepository.rotate(
            oldTokenHash = refreshToken.tokenHash,
            newExpiresAt = newExpiresAt.toEpochMilli(),
        )

        // Generate new access token
        val now = Instant.now()
        val accessTokenExpiresAt = now.plusSeconds(accessTokenTtlSeconds)
        val accessToken =
            JWT.create()
                .withSubject(refreshToken.userId.toString())
                .withClaim("type", TOKEN_TYPE_ACCESS)
                .withIssuedAt(now)
                .withExpiresAt(accessTokenExpiresAt)
                .withClaim("userId", refreshToken.userId.toString())
                .sign(Algorithm.HMAC256(secretKey))

        return TokenPair(
            accessToken = accessToken,
            refreshToken = newRefreshTokenString,
            accessTokenExpiresAt = accessTokenExpiresAt.toEpochMilli(),
            refreshTokenExpiresAt = newExpiresAt.toEpochMilli(),
        )
    }

    /**
     * Revoke a refresh token (logout).
     *
     * @param refreshTokenString The refresh token string
     * @return true if revoked, false if not found
     */
    fun revokeRefreshToken(refreshTokenString: String): Boolean {
        val refreshTokenHash = RefreshTokenRepository.hashToken(refreshTokenString)
        return refreshTokenRepository.revoke(refreshTokenHash, "logout")
    }

    /**
     * Revoke all refresh tokens for a user (e.g., password change).
     *
     * @param userId The user ID
     * @return Number of tokens revoked
     */
    fun revokeAllRefreshTokens(userId: UUID): Int {
        return refreshTokenRepository.revokeAllByUserId(userId, "password_change")
    }

    /**
     * Clean up expired and old revoked tokens.
     * Should be called periodically (e.g., daily cron job).
     *
     * @param olderThanDays Delete tokens older than this many days
     * @return Number of tokens deleted
     */
    fun cleanupExpiredTokens(olderThanDays: Int = 30): Int {
        return refreshTokenRepository.cleanup(olderThanDays)
    }
}
