package com.mel.bubblenotes.repositories

import com.mel.bubblenotes.models.Note
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AITaskRepositoryTest {
    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: AITaskRepository
    private lateinit var noteRepository: NoteRepository

    fun setup() {
        // Setup H2 in-memory database with PostgreSQL compatibility mode
        // Use unique database name per test to avoid data leakage between tests
        val uniqueDbName = "test_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"
        val hikariConfig =
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:$uniqueDbName;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
                username = "sa"
                password = ""
                // Increase pool size to handle concurrent test operations
                maximumPoolSize = 10
                minimumIdle = 2
            }
        dataSource = HikariDataSource(hikariConfig)

        // Run Flyway migrations to create schema
        try {
            val flyway =
                Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
            flyway.migrate()

            // Disable referential integrity for testing
            dataSource.connection.createStatement().use { stmt ->
                stmt.execute("SET REFERENTIAL_INTEGRITY FALSE")
            }
        } catch (e: Exception) {
            dataSource.close()
            throw e
        }

        repository = AITaskRepository(dataSource)
        noteRepository = NoteRepository(dataSource)
    }

    fun teardown() {
        dataSource.close()
    }

    @Test
    fun testCreateAITask() {
        setup()
        try {
            // Create a note first
            val userId = UUID.randomUUID()
            val note =
                Note(
                    id = 0,
                    userId = userId,
                    title = "Test Note",
                    content = "Test content",
                    isPublished = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            val noteId = noteRepository.create(note)

            // Create AI task for the note
            val taskId = repository.create(noteId)

            assertTrue(taskId > 0, "Created task should have a positive ID")

            // Verify task was created
            val task = repository.findById(taskId)
            assertNotNull(task, "Task should not be null")
            assertEquals(noteId, task.noteId, "Task should reference correct note")
            assertEquals(AITaskRepository.Status.PENDING, task.status, "Task should be in PENDING state")
        } finally {
            teardown()
        }
    }

    @Test
    fun testSetProcessing() {
        setup()
        try {
            // Create note and task
            val userId = UUID.randomUUID()
            val note =
                Note(
                    id = 0,
                    userId = userId,
                    title = "Test Note",
                    content = "Test content",
                    isPublished = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            val noteId = noteRepository.create(note)
            val taskId = repository.create(noteId)

            // Set task to processing
            val success = repository.setProcessing(taskId, "worker-1", 300000)
            assertTrue(success, "Setting task to processing should succeed")

            // Verify status changed
            val task = repository.findById(taskId)
            assertNotNull(task)
            assertEquals(AITaskRepository.Status.PROCESSING, task.status, "Task should be in PROCESSING state")
            assertEquals("worker-1", task.workerId, "Worker ID should be set")
            assertTrue(task.lockedAt != null, "Locked at should be set")
            assertEquals(300000L, task.lockTimeout, "Lock timeout should be set")
        } finally {
            teardown()
        }
    }

    @Test
    fun testSetCompleted() {
        setup()
        try {
            // Create note and task
            val userId = UUID.randomUUID()
            val note =
                Note(
                    id = 0,
                    userId = userId,
                    title = "Test Note",
                    content = "Test content",
                    isPublished = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            val noteId = noteRepository.create(note)
            val taskId = repository.create(noteId)

            // Set task to processing first
            repository.setProcessing(taskId, "worker-1")

            // Complete the task with results
            val result =
                AITaskRepository.AITaskResult(
                    aiTitle = "AI Enhanced Title",
                    aiSummary = "This is an AI-generated summary",
                    aiTags = listOf("ai", "enhanced"),
                )
            val success = repository.setCompleted(taskId, result)
            assertTrue(success, "Completing task should succeed")

            // Verify completion
            val task = repository.findById(taskId)
            assertNotNull(task)
            assertEquals(AITaskRepository.Status.COMPLETED, task.status, "Task should be in COMPLETED state")
            assertNotNull(task.result, "Result should not be null")
            assertEquals("AI Enhanced Title", task.result?.aiTitle, "AI title should match")
            assertEquals("This is an AI-generated summary", task.result?.aiSummary, "AI summary should match")
            assertEquals(listOf("ai", "enhanced"), task.result?.aiTags, "AI tags should match")
        } finally {
            teardown()
        }
    }

    @Test
    fun testSetFailed() {
        setup()
        try {
            // Create note and task
            val userId = UUID.randomUUID()
            val note =
                Note(
                    id = 0,
                    userId = userId,
                    title = "Test Note",
                    content = "Test content",
                    isPublished = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            val noteId = noteRepository.create(note)
            val taskId = repository.create(noteId)

            // Set task to failed
            val success = repository.setFailed(taskId, "OpenAI API error: Invalid API key")
            assertTrue(success, "Setting task to failed should succeed")

            // Verify failure
            val task = repository.findById(taskId)
            assertNotNull(task)
            assertEquals(AITaskRepository.Status.FAILED, task.status, "Task should be in FAILED state")
            assertEquals("OpenAI API error: Invalid API key", task.errorMessage, "Error message should match")
        } finally {
            teardown()
        }
    }

    @Test
    fun testFindPendingTasks() {
        setup()
        try {
            // Create multiple notes and tasks
            val userId = UUID.randomUUID()
            repeat(3) { i ->
                val note =
                    Note(
                        id = 0,
                        userId = userId,
                        title = "Note $i",
                        content = "Content $i",
                        isPublished = true,
                        createdAt = System.currentTimeMillis() + i,
                        updatedAt = System.currentTimeMillis() + i,
                    )
                val noteId = noteRepository.create(note)
                repository.create(noteId)
            }

            // Find pending tasks
            val tasks = repository.findPendingTasks(10)
            assertEquals(3, tasks.size, "Should find 3 pending tasks")

            // All should be in PENDING state
            tasks.forEach { task ->
                assertEquals(AITaskRepository.Status.PENDING, task.status, "All tasks should be PENDING")
            }
        } finally {
            teardown()
        }
    }

    @Test
    fun testFindPendingTasks_Limited() {
        setup()
        try {
            // Create multiple notes and tasks
            val userId = UUID.randomUUID()
            repeat(5) { i ->
                val note =
                    Note(
                        id = 0,
                        userId = userId,
                        title = "Note $i",
                        content = "Content $i",
                        isPublished = true,
                        createdAt = System.currentTimeMillis() + i,
                        updatedAt = System.currentTimeMillis() + i,
                    )
                val noteId = noteRepository.create(note)
                repository.create(noteId)
            }

            // Find only 2 pending tasks
            val tasks = repository.findPendingTasks(2)
            assertEquals(2, tasks.size, "Should find only 2 pending tasks when limit is 2")
        } finally {
            teardown()
        }
    }

    @Test
    fun testFindById_NotFound() {
        setup()
        try {
            val task = repository.findById(99999)
            assertEquals(null, task, "Should return null for non-existent task")
        } finally {
            teardown()
        }
    }

    @Test
    fun testDelete() {
        setup()
        try {
            // Create note and task
            val userId = UUID.randomUUID()
            val note =
                Note(
                    id = 0,
                    userId = userId,
                    title = "Test Note",
                    content = "Test content",
                    isPublished = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            val noteId = noteRepository.create(note)
            val taskId = repository.create(noteId)

            // Delete the task
            val success = repository.delete(taskId)
            assertTrue(success, "Deleting task should succeed")

            // Verify task is deleted
            val task = repository.findById(taskId)
            assertEquals(null, task, "Task should be deleted")
        } finally {
            teardown()
        }
    }

    @Test
    fun testDeleteOlderThan() {
        setup()
        try {
            // Create note and task
            val userId = UUID.randomUUID()
            val now = System.currentTimeMillis()
            val note =
                Note(
                    id = 0,
                    userId = userId,
                    title = "Test Note",
                    content = "Test content",
                    isPublished = true,
                    createdAt = now,
                    updatedAt = now,
                )
            val noteId = noteRepository.create(note)
            val taskId = repository.create(noteId)

            // Manually set completed_at to old timestamp
            val oldTimestamp = now - 1000000
            dataSource.connection.createStatement().use { stmt ->
                stmt.execute("UPDATE ai_tasks SET completed_at = $oldTimestamp WHERE id = $taskId")
            }

            // Delete tasks older than a recent timestamp
            val deletedCount = repository.deleteOlderThan(now)
            assertEquals(1, deletedCount, "Should delete 1 old task")

            // Verify task is deleted
            val task = repository.findById(taskId)
            assertEquals(null, task, "Task should be deleted")
        } finally {
            teardown()
        }
    }

    @Test
    fun testSetProcessing_DoesNotChangeAlreadyProcessing() {
        setup()
        try {
            // Create note and task
            val userId = UUID.randomUUID()
            val note =
                Note(
                    id = 0,
                    userId = userId,
                    title = "Test Note",
                    content = "Test content",
                    isPublished = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            val noteId = noteRepository.create(note)
            val taskId = repository.create(noteId)

            // First worker claims the task
            val firstSuccess = repository.setProcessing(taskId, "worker-1")
            assertTrue(firstSuccess, "First worker should claim the task")

            // Second worker tries to claim - should fail
            val secondSuccess = repository.setProcessing(taskId, "worker-2")
            assertTrue(!secondSuccess, "Second worker should not be able to claim already processing task")

            // Verify worker ID is still the first worker
            val task = repository.findById(taskId)
            assertEquals("worker-1", task?.workerId, "Worker ID should still be worker-1")
        } finally {
            teardown()
        }
    }

    @Test
    fun testSerializationOfAITaskResult() {
        setup()
        try {
            val json = Json { ignoreUnknownKeys = true }

            // Create a result object
            val result =
                AITaskRepository.AITaskResult(
                    aiTitle = "Test Title",
                    aiSummary = "Test summary with special chars: äöü",
                    aiTags = listOf("tag1", "tag2", "tag3"),
                )

            // Serialize to JSON
            val jsonString = json.encodeToString(result)
            assertNotNull(jsonString, "Serialization should produce non-null JSON")

            // Deserialize back
            val deserialized = json.decodeFromString<AITaskRepository.AITaskResult>(jsonString)
            assertEquals(result.aiTitle, deserialized.aiTitle, "Title should match after round-trip")
            assertEquals(result.aiSummary, deserialized.aiSummary, "Summary should match after round-trip")
            assertEquals(result.aiTags, deserialized.aiTags, "Tags should match after round-trip")
        } finally {
            teardown()
        }
    }
}
