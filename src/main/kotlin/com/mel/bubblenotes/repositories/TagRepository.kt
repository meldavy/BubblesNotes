package com.mel.bubblenotes.repositories

import com.mel.bubblenotes.models.Tag
import java.sql.Connection
import java.util.UUID

open class TagRepository(private val connection: Connection) {
    fun findById(tagId: Long): Tag? {
        val sql =
            """
            SELECT id, user_id, name, created_at FROM tags WHERE id = ?
            """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, tagId)
            val rs = stmt.executeQuery()
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

    fun create(tag: Tag): Long {
        val sql =
            """
            INSERT INTO tags (user_id, name, created_at)
            VALUES (?, ?, ?)
            """.trimIndent()

        connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setObject(1, tag.userId)
            stmt.setString(2, tag.name)
            stmt.setLong(3, tag.createdAt)

            stmt.executeUpdate()
            val rs = stmt.generatedKeys
            if (rs.next()) {
                return rs.getLong(1)
            }
            throw Exception("Failed to create tag")
        }
    }

    fun findByUserId(userId: UUID): List<Tag> {
        val sql =
            """
            SELECT id, user_id, name, created_at
            FROM tags WHERE user_id = ?
            ORDER BY name ASC
            """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, userId)
            val rs = stmt.executeQuery()

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

    fun findByNameAndUserId(
        name: String,
        userId: UUID,
    ): Tag? {
        val sql =
            """
            SELECT id, user_id, name, created_at
            FROM tags WHERE user_id = ? AND LOWER(name) = LOWER(?)
            """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, userId)
            stmt.setString(2, name)
            val rs = stmt.executeQuery()

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

    fun delete(
        tagId: Long,
        userId: UUID,
    ): Boolean {
        val sql = "DELETE FROM tags WHERE id = ? AND user_id = ?"

        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, tagId)
            stmt.setObject(2, userId)
            return stmt.executeUpdate() > 0
        }
    }
}
