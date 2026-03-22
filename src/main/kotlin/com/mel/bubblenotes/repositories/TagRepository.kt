package com.mel.bubblenotes.repositories

import com.mel.bubblenotes.models.Tag
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.util.UUID

open class TagRepository(private val dataSource: HikariDataSource) {
    /**
     * Get a fresh connection from the pool for each database operation.
     * This prevents "Connection is closed" errors that occur when storing
     * a single connection across multiple requests.
     */
    private fun getConnection(): Connection = dataSource.connection

    fun findById(tagId: Long): Tag? {
        val sql =
            """
            SELECT id, user_id, name, created_at FROM tags WHERE id = ?
            """.trimIndent()
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, tagId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return Tag(
                            id = rs.getLong("id"),
                            userId = rs.getObject("user_id", UUID::class.java),
                            name = rs.getString("name"),
                            createdAt = rs.getLong("created_at"),
                        )
                    }
                    return null
                }
            }
        }
    }

    fun create(tag: Tag): Long {
        val sql =
            """
            INSERT INTO tags (user_id, name, created_at)
            VALUES (?, ?, ?)
            """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
                stmt.setObject(1, tag.userId)
                stmt.setString(2, tag.name)
                stmt.setLong(3, tag.createdAt)

                stmt.executeUpdate()
                stmt.generatedKeys.use { rs ->
                    if (rs.next()) {
                        return rs.getLong(1)
                    }
                }
                throw Exception("Failed to create tag")
            }
        }
    }

    fun findByUserId(userId: UUID): List<Tag> {
        val sql =
            """
            SELECT id, user_id, name, created_at
            FROM tags WHERE user_id = ?
            ORDER BY name ASC
            """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, userId)
                stmt.executeQuery().use { rs ->
                    return buildList {
                        while (rs.next()) {
                            add(
                                Tag(
                                    id = rs.getLong("id"),
                                    userId = rs.getObject("user_id", UUID::class.java),
                                    name = rs.getString("name"),
                                    createdAt = rs.getLong("created_at"),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun findByNameAndUserId(
        name: String,
        userId: UUID,
    ): Tag? {
        val sql =
            """
            SELECT id, user_id, name, created_at
            FROM tags WHERE user_id = ? AND LOWER(name) = LOWER(?)
            """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setString(2, name)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return Tag(
                            id = rs.getLong("id"),
                            userId = rs.getObject("user_id", UUID::class.java),
                            name = rs.getString("name"),
                            createdAt = rs.getLong("created_at"),
                        )
                    }
                    return null
                }
            }
        }
    }

    fun delete(
        tagId: Long,
        userId: UUID,
    ): Boolean {
        val sql = "DELETE FROM tags WHERE id = ? AND user_id = ?"

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, tagId)
                stmt.setObject(2, userId)
                return stmt.executeUpdate() > 0
            }
        }
    }
}
