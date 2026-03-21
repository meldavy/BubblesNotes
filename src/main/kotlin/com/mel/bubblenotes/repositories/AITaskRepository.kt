package com.mel.bubblenotes.repositories

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection

/**
 * Repository for AI task queue operations.
 * Follows testability constitution: class is open for mocking in tests.
 */
open class AITaskRepository(private val connection: Connection) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * AI task status enum.
     */
    enum class Status {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
    }

    /**
     * AI task result data class.
     */
    @Serializable
    data class AITaskResult(
        val aiTitle: String? = null,
        val aiSummary: String? = null,
        val aiTags: List<String> = emptyList(),
    )

    /**
     * Create a new AI task for processing.
     * @param noteId The note ID to process
     * @return The generated task ID
     */
    fun create(noteId: Long): Long {
        val sql =
            """
            INSERT INTO ai_tasks (note_id, status)
            VALUES (?, 'pending')
            """.trimIndent()

        connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setLong(1, noteId)
            stmt.executeUpdate()
            val rs = stmt.generatedKeys
            if (rs.next()) {
                return rs.getLong(1)
            }
            throw Exception("Failed to create AI task")
        }
    }

    /**
     * Get a task by ID.
     * @param taskId The task ID
     * @return The task or null if not found
     */
    fun findById(taskId: Long): AITask? {
        val sql =
            """
            SELECT id, note_id, status, result, error_message, started_at, completed_at, worker_id, locked_at, lock_timeout
            FROM ai_tasks WHERE id = ?
            """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, taskId)
            val rs = stmt.executeQuery()

            if (rs.next()) {
                return AITask(
                    id = rs.getLong("id"),
                    noteId = rs.getLong("note_id"),
                    status = Status.valueOf(rs.getString("status").uppercase()),
                    result = rs.getString("result")?.let { json.decodeFromString<AITaskResult>(it) },
                    errorMessage = rs.getString("error_message"),
                    startedAt = rs.getLongOrNull("started_at"),
                    completedAt = rs.getLongOrNull("completed_at"),
                    workerId = rs.getString("worker_id"),
                    lockedAt = rs.getLongOrNull("locked_at"),
                    lockTimeout = rs.getLongOrNull("lock_timeout"),
                )
            }
            return null
        }
    }

    /**
     * Get pending tasks for processing.
     * @param limit Maximum number of tasks to return
     * @return List of pending tasks
     */
    fun findPendingTasks(limit: Int = 10): List<AITask> {
        val currentTime = System.currentTimeMillis()
        val sql =
            """
            SELECT id, note_id, status, result, error_message, started_at, completed_at, worker_id, locked_at, lock_timeout
            FROM ai_tasks
            WHERE status = 'pending'
               OR (status = 'processing' AND locked_at + lock_timeout < ?)
            ORDER BY id ASC
            LIMIT $limit
            """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, currentTime)
            val rs = stmt.executeQuery()
            return buildList {
                while (rs.next()) {
                    add(
                        AITask(
                            id = rs.getLong("id"),
                            noteId = rs.getLong("note_id"),
                            status = Status.valueOf(rs.getString("status").uppercase()),
                            result = rs.getString("result")?.let { json.decodeFromString<AITaskResult>(it) },
                            errorMessage = rs.getString("error_message"),
                            startedAt = rs.getLongOrNull("started_at"),
                            completedAt = rs.getLongOrNull("completed_at"),
                            workerId = rs.getString("worker_id"),
                            lockedAt = rs.getLongOrNull("locked_at"),
                            lockTimeout = rs.getLongOrNull("lock_timeout"),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Update task status to processing.
     * @param taskId The task ID
     * @return true if updated, false if not found
     */
    fun setProcessing(
        taskId: Long,
        workerId: String? = null,
        lockTimeout: Long = 300000,
    ): Boolean {
        val sql =
            """
            UPDATE ai_tasks
            SET status = 'processing', started_at = ?, worker_id = ?, locked_at = ?, lock_timeout = ?
            WHERE id = ? AND status = 'pending'
            """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, System.currentTimeMillis())
            stmt.setString(2, workerId)
            stmt.setLong(3, System.currentTimeMillis())
            stmt.setLong(4, lockTimeout)
            stmt.setLong(5, taskId)
            return stmt.executeUpdate() > 0
        }
    }

    /**
     * Mark task as completed with results.
     * @param taskId The task ID
     * @param result The AI task result
     * @return true if updated, false if not found
     */
    fun setCompleted(
        taskId: Long,
        result: AITaskResult,
    ): Boolean {
        val sql =
            """
            UPDATE ai_tasks
            SET status = 'completed', result = ?, completed_at = ?
            WHERE id = ?
            """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, json.encodeToString(result))
            stmt.setLong(2, System.currentTimeMillis())
            stmt.setLong(3, taskId)
            return stmt.executeUpdate() > 0
        }
    }

    /**
     * Mark task as failed with error message.
     * @param taskId The task ID
     * @param errorMessage The error message
     * @return true if updated, false if not found
     */
    fun setFailed(
        taskId: Long,
        errorMessage: String,
    ): Boolean {
        val sql =
            """
            UPDATE ai_tasks
            SET status = 'failed', error_message = ?, completed_at = ?
            WHERE id = ?
            """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, errorMessage)
            stmt.setLong(2, System.currentTimeMillis())
            stmt.setLong(3, taskId)
            return stmt.executeUpdate() > 0
        }
    }

    /**
     * Reset a task to PENDING status for retry.
     * This clears the lock and error message, allowing the task to be reprocessed.
     * @param taskId The task ID
     * @return true if updated, false if not found
     */
    fun resetToPending(taskId: Long): Boolean {
        val sql =
            """
            UPDATE ai_tasks
            SET status = 'pending', error_message = NULL, locked_at = NULL, lock_timeout = NULL
            WHERE id = ?
            """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, taskId)
            return stmt.executeUpdate() > 0
        }
    }

    /**
     * Delete a task by ID.
     * @param taskId The task ID
     * @return true if deleted, false if not found
     */
    fun delete(taskId: Long): Boolean {
        val sql = "DELETE FROM ai_tasks WHERE id = ?"

        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, taskId)
            return stmt.executeUpdate() > 0
        }
    }

    /**
     * Delete tasks older than specified timestamp.
     * @param olderThan Timestamp in milliseconds
     * @return Number of tasks deleted
     */
    fun deleteOlderThan(olderThan: Long): Int {
        val sql = "DELETE FROM ai_tasks WHERE completed_at < ?"

        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, olderThan)
            return stmt.executeUpdate()
        }
    }
}

/**
 * AI task data class.
 */
data class AITask(
    val id: Long,
    val noteId: Long,
    val status: AITaskRepository.Status,
    val result: AITaskRepository.AITaskResult? = null,
    val errorMessage: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val workerId: String? = null,
    val lockedAt: Long? = null,
    val lockTimeout: Long? = null,
)
