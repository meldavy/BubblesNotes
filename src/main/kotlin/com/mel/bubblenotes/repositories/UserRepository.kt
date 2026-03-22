package com.mel.bubblenotes.repositories

import com.mel.bubblenotes.models.User
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.util.UUID

open class UserRepository(private val dataSource: HikariDataSource) {
    /**
     * Get a fresh connection from the pool for each database operation.
     * This prevents "Connection is closed" errors that occur when storing
     * a single connection across multiple requests.
     */
    private fun getConnection(): Connection = dataSource.connection

    /**
     * Create a new user in the database.
     * Returns the ID of the created user, or throws an exception if the user already exists.
     */
    fun create(user: User): UUID {
        val sql =
            """
            INSERT INTO users (id, email, name, given_name, family_name, picture_url, oauth_token, encryption_salt, api_key, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
                stmt.setObject(1, user.id)
                stmt.setString(2, user.email)
                stmt.setString(3, user.name)
                stmt.setString(4, user.givenName)
                stmt.setString(5, user.familyName)
                stmt.setString(6, user.pictureUrl)
                stmt.setBytes(7, user.oauthToken.toByteArray())
                stmt.setString(8, user.encryptionSalt)
                stmt.setString(9, user.apiKey)
                stmt.setLong(10, user.createdAt)
                stmt.setLong(11, user.updatedAt)

                val affectedRows = stmt.executeUpdate()
                if (affectedRows == 0) {
                    throw Exception("Failed to create user: no rows affected")
                }
                return user.id
            }
        }
    }

    /**
     * Find a user by their UUID.
     * Returns null if the user does not exist.
     */
    fun findById(id: UUID): User? {
        val sql =
            """
            SELECT id, email, name, given_name, family_name, picture_url, oauth_token, encryption_salt, api_key, created_at, updated_at
            FROM users WHERE id = ?
            """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return User(
                            id = rs.getObject("id", UUID::class.java),
                            email = rs.getString("email"),
                            name = rs.getString("name"),
                            givenName = rs.getString("given_name"),
                            familyName = rs.getString("family_name"),
                            pictureUrl = rs.getString("picture_url"),
                            oauthToken = String(rs.getBytes("oauth_token")),
                            encryptionSalt = rs.getString("encryption_salt"),
                            apiKey = rs.getString("api_key"),
                            createdAt = rs.getLong("created_at"),
                            updatedAt = rs.getLong("updated_at"),
                        )
                    }
                    return null
                }
            }
        }
    }

    /**
     * Find a user by their email address.
     * Returns null if the user does not exist.
     */
    fun findByEmail(email: String): User? {
        val sql =
            """
            SELECT id, email, name, given_name, family_name, picture_url, oauth_token, encryption_salt, api_key, created_at, updated_at
            FROM users WHERE LOWER(email) = LOWER(?)
            """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, email)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return User(
                            id = rs.getObject("id", UUID::class.java),
                            email = rs.getString("email"),
                            name = rs.getString("name"),
                            givenName = rs.getString("given_name"),
                            familyName = rs.getString("family_name"),
                            pictureUrl = rs.getString("picture_url"),
                            oauthToken = String(rs.getBytes("oauth_token")),
                            encryptionSalt = rs.getString("encryption_salt"),
                            apiKey = rs.getString("api_key"),
                            createdAt = rs.getLong("created_at"),
                            updatedAt = rs.getLong("updated_at"),
                        )
                    }
                    return null
                }
            }
        }
    }

    /**
     * Update an existing user in the database.
     * Returns true if the user was updated, false if the user does not exist.
     */
    fun update(user: User): Boolean {
        val sql =
            """
            UPDATE users
            SET email = ?, name = ?, given_name = ?, family_name = ?, picture_url = ?,
                oauth_token = ?, encryption_salt = ?, api_key = ?, updated_at = ?
            WHERE id = ?
            """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, user.email)
                stmt.setString(2, user.name)
                stmt.setString(3, user.givenName)
                stmt.setString(4, user.familyName)
                stmt.setString(5, user.pictureUrl)
                stmt.setBytes(6, user.oauthToken.toByteArray())
                stmt.setString(7, user.encryptionSalt)
                stmt.setString(8, user.apiKey)
                stmt.setLong(9, user.updatedAt)
                stmt.setObject(10, user.id)

                return stmt.executeUpdate() > 0
            }
        }
    }
}
