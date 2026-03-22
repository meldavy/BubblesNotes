package com.mel.bubblenotes.repositories

import com.zaxxer.hikari.HikariDataSource
import java.security.MessageDigest
import java.sql.Connection
import java.sql.Statement
import java.util.Base64
import java.util.UUID

/**
 * Repository for refresh token storage and rotation.
 * Follows testability constitution: class is open for mocking in tests.
 */
open class RefreshTokenRepository(private val dataSource: HikariDataSource) {
    /**
     * Get a fresh connection from the pool for each database operation.
     * This prevents "Connection is closed" errors that occur when storing
     * a single connection across multiple requests.
     */
    private fun getConnection(): Connection = dataSource.connection

    /**
     * Refresh token data class.
     */
    data class RefreshToken(
        val id: Long,
        val userId: UUID,
        val tokenHash: String,
        val parentTokenHash: String?,
        val expiresAt: Long,
        val createdAt: Long,
        val usedAt: Long?,
        val revokedAt: Long?,
        val revokeReason: String?,
        val isCurrent: Boolean,
    )

    /**
     * Hash a refresh token using SHA-256.
     */
    companion object {
        fun hashToken(token: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(token.toByteArray())
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Create a new refresh token record.
     * @param userId The user ID
     * @param token The refresh token (will be hashed before storage)
     * @param expiresAt Expiration timestamp in milliseconds
     * @param parentTokenHash Hash of the parent token in rotation chain (optional)
     * @return The generated token ID
     */
    fun create(
        userId: UUID,
        token: String,
        expiresAt: Long,
        parentTokenHash: String? = null,
    ): Long {
        val sql =
            """
            INSERT INTO refresh_tokens (user_id, token_hash, parent_token_hash, expires_at, created_at, is_current)
            VALUES (?, ?, ?, ?, ?, TRUE)
            """.trimIndent()

        val tokenHash = hashToken(token)
        val currentTime = System.currentTimeMillis()

        getConnection().use { conn ->
            conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { stmt ->
                stmt.queryTimeout = 10
                stmt.setObject(1, userId)
                stmt.setString(2, tokenHash)
                stmt.setString(3, parentTokenHash)
                stmt.setLong(4, expiresAt)
                stmt.setLong(5, currentTime)
                stmt.executeUpdate()

                stmt.generatedKeys.use { rs ->
                    if (rs.next()) {
                        return rs.getLong(1)
                    }
                }
                throw Exception("Failed to create refresh token")
            }
        }
    }

    /**
     * Find a refresh token by its hash.
     * @param tokenHash The token hash to look up
     * @return The refresh token or null if not found
     */
    fun findByHash(tokenHash: String): RefreshToken? {
        val sql =
            """
            SELECT id, user_id, token_hash, parent_token_hash, expires_at, created_at, used_at, revoked_at, revoke_reason, is_current
            FROM refresh_tokens
            WHERE token_hash = ?
            """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.queryTimeout = 10
                stmt.setString(1, tokenHash)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return RefreshToken(
                            id = rs.getLong("id"),
                            userId = UUID.fromString(rs.getString("user_id")),
                            tokenHash = rs.getString("token_hash"),
                            parentTokenHash = rs.getString("parent_token_hash"),
                            expiresAt = rs.getLong("expires_at"),
                            createdAt = rs.getLong("created_at"),
                            usedAt = rs.getLongOrNull("used_at"),
                            revokedAt = rs.getLongOrNull("revoked_at"),
                            revokeReason = rs.getString("revoke_reason"),
                            isCurrent = rs.getBoolean("is_current"),
                        )
                    }
                    return null
                }
            }
        }
    }

    /**
     * Find the current active refresh token for a user.
     * @param userId The user ID
     * @return The current refresh token or null if not found
     */
    fun findCurrentByUserId(userId: UUID): RefreshToken? {
        val sql =
            """
            SELECT id, user_id, token_hash, parent_token_hash, expires_at, created_at, used_at, revoked_at, revoke_reason, is_current
            FROM refresh_tokens
            WHERE user_id = ? AND is_current = TRUE AND revoked_at IS NULL AND expires_at > ?
            """.trimIndent()

        val currentTime = System.currentTimeMillis()
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.queryTimeout = 10
                stmt.setObject(1, userId)
                stmt.setLong(2, currentTime)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return RefreshToken(
                            id = rs.getLong("id"),
                            userId = UUID.fromString(rs.getString("user_id")),
                            tokenHash = rs.getString("token_hash"),
                            parentTokenHash = rs.getString("parent_token_hash"),
                            expiresAt = rs.getLong("expires_at"),
                            createdAt = rs.getLong("created_at"),
                            usedAt = rs.getLongOrNull("used_at"),
                            revokedAt = rs.getLongOrNull("revoked_at"),
                            revokeReason = rs.getString("revoke_reason"),
                            isCurrent = rs.getBoolean("is_current"),
                        )
                    }
                    return null
                }
            }
        }
    }

    /**
     * Rotate a refresh token: mark old token as used and create a new one.
     * The new token is generated internally to ensure atomicity.
     * @param oldTokenHash The hash of the token being rotated
     * @param newExpiresAt Expiration timestamp for the new token
     * @return Pair of (new token hash, new token string)
     */
    fun rotate(
        oldTokenHash: String,
        newExpiresAt: Long,
    ): Pair<String, String> {
        // Generate new token string
        val secureRandom = java.security.SecureRandom()
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val newToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val newTokenHash = hashToken(newToken)

        getConnection().use { conn ->
            conn.autoCommit = false
            try {
                // Fail fast on lock contention inside this transaction
                conn.createStatement().use { s ->
                    s.queryTimeout = 5
                    s.execute("SET LOCAL lock_timeout = '5s'")
                }
                // Mark old token as used
                val updateSql =
                    """
                    UPDATE refresh_tokens
                    SET used_at = ?, is_current = FALSE
                    WHERE token_hash = ? AND is_current = TRUE AND revoked_at IS NULL
                    """.trimIndent()

                val currentTime = System.currentTimeMillis()
                conn.prepareStatement(updateSql).use { stmt ->
                    stmt.queryTimeout = 10
                    stmt.setLong(1, currentTime)
                    stmt.setString(2, oldTokenHash)
                    stmt.executeUpdate()
                }

                // Create new token with parent reference
                val insertSql =
                    """
                    INSERT INTO refresh_tokens (user_id, token_hash, parent_token_hash, expires_at, created_at, is_current)
                    SELECT user_id, ?, ?, ?, ?, TRUE
                    FROM refresh_tokens WHERE token_hash = ?
                    """.trimIndent()

                conn.prepareStatement(insertSql).use { stmt ->
                    stmt.queryTimeout = 10
                    stmt.setString(1, newTokenHash)
                    stmt.setString(2, oldTokenHash)
                    stmt.setLong(3, newExpiresAt)
                    stmt.setLong(4, currentTime)
                    stmt.setString(5, oldTokenHash)
                    stmt.executeUpdate()
                }

                conn.commit()
                return Pair(newTokenHash, newToken)
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    /**
     * Revoke a refresh token.
     * @param tokenHash The token hash to revoke
     * @param reason The reason for revocation
     * @return true if revoked, false if not found
     */
    fun revoke(
        tokenHash: String,
        reason: String = "logout",
    ): Boolean {
        val sql =
            """
            UPDATE refresh_tokens
            SET revoked_at = ?, revoke_reason = ?, is_current = FALSE
            WHERE token_hash = ?
            """.trimIndent()

        val currentTime = System.currentTimeMillis()
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.queryTimeout = 10
                stmt.setLong(1, currentTime)
                stmt.setString(2, reason)
                stmt.setString(3, tokenHash)
                return stmt.executeUpdate() > 0
            }
        }
    }

    /**
     * Revoke all refresh tokens for a user (e.g., on password change).
     * @param userId The user ID
     * @param reason The reason for revocation
     * @return Number of tokens revoked
     */
    fun revokeAllByUserId(
        userId: UUID,
        reason: String = "password_change",
    ): Int {
        val sql =
            """
            UPDATE refresh_tokens
            SET revoked_at = ?, revoke_reason = ?, is_current = FALSE
            WHERE user_id = ? AND revoked_at IS NULL
            """.trimIndent()

        val currentTime = System.currentTimeMillis()
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.queryTimeout = 10
                stmt.setLong(1, currentTime)
                stmt.setString(2, reason)
                stmt.setObject(3, userId)
                return stmt.executeUpdate()
            }
        }
    }

    /**
     * Detect token theft: if a token is used but its parent was already used,
     * it means the token was stolen and used after the legitimate user refreshed.
     * @param tokenHash The token hash being validated
     * @return true if theft is detected
     */
    fun detectTheft(tokenHash: String): Boolean {
        val sql =
            """
            SELECT rt1.token_hash, rt1.parent_token_hash
            FROM refresh_tokens rt1
            JOIN refresh_tokens rt2 ON rt1.parent_token_hash = rt2.token_hash
            WHERE rt1.token_hash = ? AND rt2.used_at IS NOT NULL AND rt1.used_at IS NULL
            """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.queryTimeout = 10
                stmt.setString(1, tokenHash)
                stmt.executeQuery().use { rs ->
                    return rs.next() // If a row exists, theft was detected
                }
            }
        }
    }

    /**
     * Revoke all tokens in a rotation chain when theft is detected.
     * @param tokenHash The stolen token hash
     * @param userId The user ID
     */
    fun revokeTokenChain(
        tokenHash: String,
        userId: UUID,
    ) {
        getConnection().use { conn ->
            conn.autoCommit = false
            try {
                // Fail fast on lock contention inside this transaction
                conn.createStatement().use { s ->
                    s.queryTimeout = 5
                    s.execute("SET LOCAL lock_timeout = '5s'")
                }
                val currentTime = System.currentTimeMillis()

                // Revoke the stolen token
                val revokeSql =
                    """
                    UPDATE refresh_tokens
                    SET revoked_at = ?, revoke_reason = 'theft_detected', is_current = FALSE
                    WHERE token_hash = ?
                    """.trimIndent()

                conn.prepareStatement(revokeSql).use { stmt ->
                    stmt.queryTimeout = 10
                    stmt.setLong(1, currentTime)
                    stmt.setString(2, tokenHash)
                    stmt.executeUpdate()
                }

                // Revoke all child tokens (tokens that have the stolen token as parent)
                val revokeChildrenSql =
                    """
                    UPDATE refresh_tokens
                    SET revoked_at = ?, revoke_reason = 'theft_detected', is_current = FALSE
                    WHERE parent_token_hash = ?
                    """.trimIndent()

                conn.prepareStatement(revokeChildrenSql).use { stmt ->
                    stmt.queryTimeout = 10
                    stmt.setLong(1, currentTime)
                    stmt.setString(2, tokenHash)
                    stmt.executeUpdate()
                }

                // Revoke all other tokens for this user
                val revokeAllSql =
                    """
                    UPDATE refresh_tokens
                    SET revoked_at = ?, revoke_reason = 'theft_detected', is_current = FALSE
                    WHERE user_id = ? AND revoked_at IS NULL
                    """.trimIndent()

                conn.prepareStatement(revokeAllSql).use { stmt ->
                    stmt.queryTimeout = 10
                    stmt.setLong(1, currentTime)
                    stmt.setObject(2, userId)
                    stmt.executeUpdate()
                }

                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    /**
     * Clean up expired and old revoked tokens.
     * @param olderThanDays Delete tokens older than this many days
     * @return Number of tokens deleted
     */
    fun cleanup(olderThanDays: Int = 30): Int {
        val sql =
            """
            DELETE FROM refresh_tokens
            WHERE (expires_at < ? AND expires_at < EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000 - ?)
               OR (revoked_at IS NOT NULL AND revoked_at < EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000 - ?)
            """.trimIndent()

        val currentTime = System.currentTimeMillis()
        val cutoffTime = currentTime - (olderThanDays.toLong() * 24 * 60 * 60 * 1000)

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.queryTimeout = 10
                stmt.setLong(1, cutoffTime)
                stmt.setLong(2, olderThanDays.toLong() * 24 * 60 * 60 * 1000)
                stmt.setLong(3, olderThanDays.toLong() * 24 * 60 * 60 * 1000)
                return stmt.executeUpdate()
            }
        }
    }
}
