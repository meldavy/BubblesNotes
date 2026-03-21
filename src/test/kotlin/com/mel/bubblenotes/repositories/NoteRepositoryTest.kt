package com.mel.bubblenotes.repositories

import com.mel.bubblenotes.models.Note
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NoteRepositoryTest {
    private lateinit var dataSource: HikariDataSource
    private lateinit var noteRepository: NoteRepository

    fun setup() {
        // Setup H2 in-memory database with PostgreSQL compatibility mode
        val hikariConfig =
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
                username = "sa"
                password = ""
            }
        dataSource = HikariDataSource(hikariConfig)

        // Run Flyway migrations to create schema (same as production)
        try {
            val flyway =
                Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
            flyway.migrate()

            // Disable referential integrity for testing - allows creating notes with arbitrary user IDs
            dataSource.connection.createStatement().use { stmt ->
                stmt.execute("SET REFERENTIAL_INTEGRITY FALSE")
            }
        } catch (e: Exception) {
            dataSource.close()
            throw e
        }

        noteRepository = NoteRepository(dataSource.connection)
    }

    fun teardown() {
        dataSource.close()
    }

    @Test
    fun testCreateAndFindNote() =
        runBlocking {
            setup()
            try {
                val userId = UUID.randomUUID()
                val now = System.currentTimeMillis()

                // Create a note
                val note =
                    Note(
                        id = 0,
                        userId = userId,
                        title = "Test Note",
                        content = "This is test content",
                        isPublished = true,
                        createdAt = now,
                        updatedAt = now,
                    )

                val createdId = noteRepository.create(note)
                assertTrue(createdId > 0, "Created note should have a positive ID")

                // Find the note by ID
                val foundNote = noteRepository.findById(createdId)
                assertNotNull(foundNote, "Found note should not be null")
                assertEquals("Test Note", foundNote.title, "Title should match")
                assertEquals("This is test content", foundNote.content, "Content should match")
                assertEquals(userId, foundNote.userId, "User ID should match")
            } finally {
                teardown()
            }
        }

    @Test
    fun testUpdateNote() =
        runBlocking {
            setup()
            try {
                val userId = UUID.randomUUID()
                val now = System.currentTimeMillis()

                // Create a note first
                val note =
                    Note(
                        id = 0,
                        userId = userId,
                        title = "Original Title",
                        content = "Original content",
                        isPublished = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                val createdId = noteRepository.create(note)

                // Update the note
                val updatedNote =
                    Note(
                        id = createdId,
                        userId = userId,
                        title = "Updated Title",
                        content = "Updated content",
                        isPublished = false,
                        createdAt = now,
                        updatedAt = System.currentTimeMillis(),
                    )

                val success = noteRepository.update(updatedNote)
                assertTrue(success, "Update should succeed")

                // Verify the update
                val foundNote = noteRepository.findById(createdId)
                assertNotNull(foundNote)
                assertEquals("Updated Title", foundNote.title)
                assertEquals("Updated content", foundNote.content)
                assertFalse(foundNote.isPublished)
            } finally {
                teardown()
            }
        }

    @Test
    fun testDeleteNote() =
        runBlocking {
            setup()
            try {
                val userId = UUID.randomUUID()
                val now = System.currentTimeMillis()

                // Create a note first
                val note =
                    Note(
                        id = 0,
                        userId = userId,
                        title = "To Delete",
                        content = "Content to delete",
                        isPublished = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                val createdId = noteRepository.create(note)

                // Delete the note
                val success = noteRepository.delete(createdId, userId)
                assertTrue(success, "Delete should succeed")

                // Verify the note is deleted
                val foundNote = noteRepository.findById(createdId)
                assertEquals(null, foundNote, "Note should be deleted")
            } finally {
                teardown()
            }
        }

    @Test
    fun testFindByUserId() =
        runBlocking {
            setup()
            try {
                val userId = UUID.randomUUID()
                val otherUserId = UUID.randomUUID()
                val now = System.currentTimeMillis()

                // Create multiple notes for the same user
                repeat(3) { i ->
                    val note =
                        Note(
                            id = 0,
                            userId = userId,
                            title = "Note $i",
                            content = "Content $i",
                            isPublished = true,
                            createdAt = now + i,
                            updatedAt = now + i,
                        )
                    noteRepository.create(note)
                }

                // Create a note for another user
                val otherNote =
                    Note(
                        id = 0,
                        userId = otherUserId,
                        title = "Other User's Note",
                        content = "Should not appear",
                        isPublished = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                noteRepository.create(otherNote)

                // Find notes by user ID
                val notes = noteRepository.findByUserId(userId, limit = 10)
                assertEquals(3, notes.size, "Should find exactly 3 notes for the user")

                // Verify none of them belong to the other user
                notes.forEach { note ->
                    assertEquals(userId, note.userId, "All notes should belong to the correct user")
                }
            } finally {
                teardown()
            }
        }

    @Test
    fun testCountByUserId() =
        runBlocking {
            setup()
            try {
                val userId = UUID.randomUUID()
                val now = System.currentTimeMillis()

                // Create multiple notes
                repeat(5) { i ->
                    val note =
                        Note(
                            id = 0,
                            userId = userId,
                            title = "Note $i",
                            content = "Content $i",
                            isPublished = true,
                            createdAt = now + i,
                            updatedAt = now + i,
                        )
                    noteRepository.create(note)
                }

                // Count notes for the user
                val count = noteRepository.countByUserId(userId)
                assertEquals(5, count, "Should have 5 notes")
            } finally {
                teardown()
            }
        }

    @Test
    fun testNoteWithNullTitle() =
        runBlocking {
            setup()
            try {
                val userId = UUID.randomUUID()
                val now = System.currentTimeMillis()

                // Create a note with null title
                val note =
                    Note(
                        id = 0,
                        userId = userId,
                        title = null,
                        content = "Content without title",
                        isPublished = true,
                        createdAt = now,
                        updatedAt = now,
                    )

                val createdId = noteRepository.create(note)
                assertTrue(createdId > 0)

                val foundNote = noteRepository.findById(createdId)
                assertNotNull(foundNote)
                assertEquals(null, foundNote.title)
            } finally {
                teardown()
            }
        }

    @Test
    fun testSearchByUserId_SearchesTitle() =
        runBlocking {
            setup()
            try {
                val userId = UUID.randomUUID()
                val now = System.currentTimeMillis()

                // Create notes with different titles
                val note1 =
                    Note(
                        id = 0,
                        userId = userId,
                        title = "Meeting Notes",
                        content = "Discussion about project",
                        isPublished = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                val note2 =
                    Note(
                        id = 0,
                        userId = userId,
                        title = "Shopping List",
                        content = "Milk, eggs, bread",
                        isPublished = true,
                        createdAt = now + 1,
                        updatedAt = now + 1,
                    )
                val note3 =
                    Note(
                        id = 0,
                        userId = userId,
                        title = "Random Thoughts",
                        content = "Some random content",
                        isPublished = true,
                        createdAt = now + 2,
                        updatedAt = now + 2,
                    )
                noteRepository.create(note1)
                noteRepository.create(note2)
                noteRepository.create(note3)

                // Search for "meeting" in title
                val results = noteRepository.searchByUserId(userId, "meeting", limit = 10)
                assertEquals(1, results.size, "Should find exactly 1 note matching 'meeting'")
                assertEquals("Meeting Notes", results[0].title)
            } finally {
                teardown()
            }
        }

    @Test
    fun testSearchByUserId_SearchesContent() =
        runBlocking {
            setup()
            try {
                val userId = UUID.randomUUID()
                val now = System.currentTimeMillis()

                // Create notes with different content
                val note1 =
                    Note(
                        id = 0,
                        userId = userId,
                        title = "Note 1",
                        content = "This contains milk and eggs",
                        isPublished = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                val note2 =
                    Note(
                        id = 0,
                        userId = userId,
                        title = "Note 2",
                        content = "This has bread and butter",
                        isPublished = true,
                        createdAt = now + 1,
                        updatedAt = now + 1,
                    )
                noteRepository.create(note1)
                noteRepository.create(note2)

                // Search for "milk" in content
                val results = noteRepository.searchByUserId(userId, "milk", limit = 10)
                assertEquals(1, results.size, "Should find exactly 1 note containing 'milk'")
                assertTrue(results[0].content.contains("milk"))
            } finally {
                teardown()
            }
        }

    @Test
    fun testSearchByUserId_SearchesTags() =
        runBlocking {
            setup()
            try {
                val userId = UUID.randomUUID()
                val now = System.currentTimeMillis()

                // Create notes with tags
                val note1 =
                    Note(
                        id = 0,
                        userId = userId,
                        title = "Work Note",
                        content = "Work related content",
                        isPublished = true,
                        tags = listOf("work", "important"),
                        createdAt = now,
                        updatedAt = now,
                    )
                val note2 =
                    Note(
                        id = 0,
                        userId = userId,
                        title = "Personal Note",
                        content = "Personal content",
                        isPublished = true,
                        tags = listOf("personal", "hobbies"),
                        createdAt = now + 1,
                        updatedAt = now + 1,
                    )
                noteRepository.create(note1)
                noteRepository.create(note2)

                // Search for "work" tag
                val results = noteRepository.searchByUserId(userId, "work", limit = 10)
                assertEquals(1, results.size, "Should find exactly 1 note with 'work' tag")
                assertTrue(results[0].tags.contains("work"))
            } finally {
                teardown()
            }
        }

    @Test
    fun testSearchByUserId_CaseInsensitive() =
        runBlocking {
            setup()
            try {
                val userId = UUID.randomUUID()
                val now = System.currentTimeMillis()

                // Create note with lowercase content
                val note =
                    Note(
                        id = 0,
                        userId = userId,
                        title = "Test Title",
                        content = "This has lowercase content",
                        isPublished = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                noteRepository.create(note)

                // Search with uppercase
                val results = noteRepository.searchByUserId(userId, "LOWERCASE", limit = 10)
                assertEquals(1, results.size, "Search should be case-insensitive")
            } finally {
                teardown()
            }
        }

    @Test
    fun testSearchByUserId_EmptyQueryReturnsAll() =
        runBlocking {
            setup()
            try {
                val userId = UUID.randomUUID()
                val now = System.currentTimeMillis()

                // Create multiple notes
                repeat(3) { i ->
                    val note =
                        Note(
                            id = 0,
                            userId = userId,
                            title = "Note $i",
                            content = "Content $i",
                            isPublished = true,
                            createdAt = now + i,
                            updatedAt = now + i,
                        )
                    noteRepository.create(note)
                }

                // Search with empty query
                val results = noteRepository.searchByUserId(userId, "", limit = 10)
                assertEquals(3, results.size, "Empty query should return all notes")
            } finally {
                teardown()
            }
        }

    @Test
    fun testSearchByUserId_NoResults() =
        runBlocking {
            setup()
            try {
                val userId = UUID.randomUUID()
                val now = System.currentTimeMillis()

                // Create a note
                val note =
                    Note(
                        id = 0,
                        userId = userId,
                        title = "Unique Title",
                        content = "Unique content here",
                        isPublished = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                noteRepository.create(note)

                // Search for something that doesn't exist
                val results = noteRepository.searchByUserId(userId, "nonexistentxyz", limit = 10)
                assertEquals(0, results.size, "Should return no results for non-matching query")
            } finally {
                teardown()
            }
        }

    @Test
    fun testSearchByUserId_OtherUserNotesNotIncluded() =
        runBlocking {
            setup()
            try {
                val userId = UUID.randomUUID()
                val otherUserId = UUID.randomUUID()
                val now = System.currentTimeMillis()

                // Create note for main user
                val note1 =
                    Note(
                        id = 0,
                        userId = userId,
                        title = "My Note",
                        content = "My content",
                        isPublished = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                // Create note for other user with same content
                val note2 =
                    Note(
                        id = 0,
                        userId = otherUserId,
                        title = "My Note",
                        content = "My content",
                        isPublished = true,
                        createdAt = now + 1,
                        updatedAt = now + 1,
                    )
                noteRepository.create(note1)
                noteRepository.create(note2)

                // Search should only return notes for the requesting user
                val results = noteRepository.searchByUserId(userId, "my", limit = 10)
                assertEquals(1, results.size, "Should only find 1 note for the requesting user")
                assertEquals(userId, results[0].userId)
            } finally {
                teardown()
            }
        }
}
