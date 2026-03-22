package com.mel.bubblenotes.api

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.mel.bubblenotes.JWTPrincipal
import com.mel.bubblenotes.models.Note
import com.mel.bubblenotes.repositories.NoteRepository
import com.mel.bubblenotes.repositories.RefreshTokenRepository
import com.mel.bubblenotes.repositories.UserRepository
import com.mel.bubblenotes.services.JWTTokenService
import com.mel.bubblenotes.services.URLPreview
import com.mel.bubblenotes.services.URLPreviewService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotesApiIntegrationTest {
    private lateinit var dataSource: HikariDataSource
    private lateinit var noteRepository: NoteRepository
    private lateinit var userRepository: UserRepository
    private lateinit var jwtTokenService: JWTTokenService
    private val testUserId = UUID.randomUUID()
    private val jsonSerializer =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private fun createAccessToken(userId: UUID): String {
        return jwtTokenService.createTokenPair(userId).accessToken
    }

    @BeforeTest
    fun setup() {
        // Setup H2 in-memory database with PostgreSQL compatibility mode
        val hikariConfig =
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
                username = "sa"
                password = ""
                // Increase pool size to handle concurrent test operations
                maximumPoolSize = 10
                minimumIdle = 2
            }
        dataSource = HikariDataSource(hikariConfig)

        // Run Flyway migrations to create schema
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

        // Clear existing data
        dataSource.connection.createStatement().use { stmt ->
            stmt.execute("DELETE FROM versions")
            stmt.execute("DELETE FROM note_tags")
            stmt.execute("DELETE FROM attachments")
            stmt.execute("DELETE FROM api_tokens")
            stmt.execute("DELETE FROM tags")
            stmt.execute("DELETE FROM notes")
            stmt.execute("DELETE FROM users")
        }

        // Create test user
        dataSource.connection.prepareStatement(
            """INSERT INTO users (id, email, name, oauth_token, encryption_salt, api_key, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
        ).use { stmt ->
            stmt.setObject(1, testUserId)
            stmt.setString(2, "test@example.com")
            stmt.setString(3, "Test User")
            stmt.setBytes(4, "test-token".toByteArray())
            stmt.setString(5, "00000000000000000000000000000000")
            stmt.setString(6, "test-api-key-12345678901234567890123456789012")
            stmt.setLong(7, System.currentTimeMillis())
            stmt.setLong(8, System.currentTimeMillis())
            stmt.executeUpdate()
        }

        // Re-enable referential integrity
        dataSource.connection.createStatement().use { stmt ->
            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE")
        }

        // Initialize repositories
        noteRepository = NoteRepository(dataSource)
        userRepository = UserRepository(dataSource)
        val refreshTokenRepository = RefreshTokenRepository(dataSource)
        jwtTokenService =
            JWTTokenService(
                refreshTokenRepository = refreshTokenRepository,
                secretKey = "test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray(),
                accessTokenTtlSeconds = 3600,
                refreshTokenTtlSeconds = 604800,
            )
    }

    @AfterTest
    fun teardown() {
        dataSource.close()
    }

    @Test
    fun `test create note`() =
        testApplication {
            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = URLPreviewService()

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val response =
                client.post("/api/v1/notes") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $accessToken")
                    setBody(NoteCreationRequest(title = "Test Note", content = "This is test content"))
                }

            println("Response status: ${response.status}")
            println("Response body: ${response.bodyAsText()}")

            assertEquals(HttpStatusCode.Created, response.status)

            val responseBody = response.bodyAsText()
            val createdNote = jsonSerializer.decodeFromString<Note>(responseBody)
            val noteId = createdNote.id
            assertTrue(noteId > 0)
        }

    @Test
    fun `test get all notes with pagination`() =
        testApplication {
            val now = System.currentTimeMillis()

            for (i in 1..5) {
                val note =
                    Note(
                        id = 0,
                        userId = testUserId,
                        title = "Note $i",
                        content = "Content $i",
                        isPublished = true,
                        previewData = null,
                        createdAt = now - i * 1000,
                        updatedAt = now - i * 1000,
                    )
                noteRepository.create(note)
            }

            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = null

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val response =
                client.get("/api/v1/notes?limit=3") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.OK, response.status)

            val responseBody = response.bodyAsText()
            val notes = jsonSerializer.decodeFromString<List<Note>>(responseBody)
            assertEquals(3, notes.size)
        }

    @Test
    fun `test infinite scroll pagination`() =
        testApplication {
            val now = System.currentTimeMillis()

            for (i in 1..10) {
                val note =
                    Note(
                        id = 0,
                        userId = testUserId,
                        title = "Note $i",
                        content = "Content $i",
                        isPublished = true,
                        previewData = null,
                        createdAt = now - i * 1000,
                        updatedAt = now - i * 1000,
                    )
                noteRepository.create(note)
            }

            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = null

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val firstResponse =
                client.get("/api/v1/notes?limit=5") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.OK, firstResponse.status)
            val firstPageNotes = jsonSerializer.decodeFromString<List<Note>>(firstResponse.bodyAsText())
            assertEquals(5, firstPageNotes.size)

            val cursor = firstPageNotes.last().id

            val secondResponse =
                client.get("/api/v1/notes?limit=5&cursor=$cursor") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.OK, secondResponse.status)
            val secondPageNotes = jsonSerializer.decodeFromString<List<Note>>(secondResponse.bodyAsText())
            assertEquals(5, secondPageNotes.size)

            val firstPageIds = firstPageNotes.map { it.id }.toSet()
            val secondPageIds = secondPageNotes.map { it.id }.toSet()
            assertTrue(firstPageIds.intersect(secondPageIds).isEmpty())
        }

    @Test
    fun `test get note by id`() =
        testApplication {
            val note =
                Note(
                    id = 0,
                    userId = testUserId,
                    title = "Single Note",
                    content = "Single note content",
                    isPublished = true,
                    previewData = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            val noteId = noteRepository.create(note)

            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = null

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val response =
                client.get("/api/v1/notes/$noteId") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.OK, response.status)

            val responseBody = response.bodyAsText()
            val retrievedNote = jsonSerializer.decodeFromString<Note>(responseBody)
            assertEquals(noteId, retrievedNote.id)
            assertEquals("Single Note", retrievedNote.title)
            assertEquals("Single note content", retrievedNote.content)
        }

    @Test
    fun `test get non-existent note returns 404`() =
        testApplication {
            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = null

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val response =
                client.get("/api/v1/notes/999999") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `test update note`() =
        testApplication {
            val note =
                Note(
                    id = 0,
                    userId = testUserId,
                    title = "Original Title",
                    content = "Original content",
                    isPublished = true,
                    previewData = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            val noteId = noteRepository.create(note)

            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = null

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val response =
                client.put("/api/v1/notes/$noteId") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $accessToken")
                    setBody(
                        NoteUpdateRequest(
                            title = "Updated Title",
                            content = "Updated content",
                            isPublished = false,
                        ),
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val responseBody = response.bodyAsText()
            val updatedNote = jsonSerializer.decodeFromString<Note>(responseBody)
            assertEquals(noteId, updatedNote.id)
            assertEquals("Updated Title", updatedNote.title)
            assertEquals("Updated content", updatedNote.content)
            assertEquals(false, updatedNote.isPublished)
        }

    @Test
    fun `test update note with partial data`() =
        testApplication {
            val note =
                Note(
                    id = 0,
                    userId = testUserId,
                    title = "Original Title",
                    content = "Original content",
                    isPublished = true,
                    previewData = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            val noteId = noteRepository.create(note)

            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = null

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val response =
                client.put("/api/v1/notes/$noteId") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $accessToken")
                    setBody(
                        NoteUpdateRequest(
                            title = "New Title",
                            content = null,
                            isPublished = null,
                        ),
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val responseBody = response.bodyAsText()
            val updatedNote = jsonSerializer.decodeFromString<Note>(responseBody)
            assertEquals("New Title", updatedNote.title)
            assertEquals("Original content", updatedNote.content)
            assertEquals(true, updatedNote.isPublished)
        }

    @Test
    fun `test update non-existent note returns 404`() =
        testApplication {
            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = null

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val response =
                client.put("/api/v1/notes/999999") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $accessToken")
                    setBody(
                        NoteUpdateRequest(
                            title = "Updated Title",
                            content = "Updated content",
                            isPublished = false,
                        ),
                    )
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `test delete note`() =
        testApplication {
            val note =
                Note(
                    id = 0,
                    userId = testUserId,
                    title = "Note to Delete",
                    content = "Content to delete",
                    isPublished = true,
                    previewData = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            val noteId = noteRepository.create(note)

            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = null

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val getResponse =
                client.get("/api/v1/notes/$noteId") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.OK, getResponse.status)

            val deleteResponse =
                client.delete("/api/v1/notes/$noteId") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

            val verifyResponse =
                client.get("/api/v1/notes/$noteId") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.NotFound, verifyResponse.status)
        }

    @Test
    fun `test delete non-existent note returns 404`() =
        testApplication {
            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = null

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val response =
                client.delete("/api/v1/notes/999999") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `test get URL previews for note with previews`() =
        testApplication {
            val previewData =
                jsonSerializer.encodeToString(
                    listOf(
                        URLPreview(
                            url = "https://example.com",
                            title = "Example Title",
                            description = "Example Description",
                            favicon = "https://example.com/favicon.ico",
                        ),
                    ),
                )

            val note =
                Note(
                    id = 0,
                    userId = testUserId,
                    title = "Note with Previews",
                    content = "Content with URLs",
                    isPublished = true,
                    previewData = previewData,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            val noteId = noteRepository.create(note)

            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = null

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val response =
                client.get("/api/v1/notes/$noteId/previews") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.OK, response.status)

            val responseBody = response.bodyAsText()
            val previews = jsonSerializer.decodeFromString<List<URLPreview>>(responseBody)
            assertEquals(1, previews.size)
            assertEquals("https://example.com", previews[0].url)
            assertEquals("Example Title", previews[0].title)
            assertEquals("Example Description", previews[0].description)
        }

    @Test
    fun `test get URL previews for note without previews`() =
        testApplication {
            val note =
                Note(
                    id = 0,
                    userId = testUserId,
                    title = "Note without Previews",
                    content = "Content without URLs",
                    isPublished = true,
                    previewData = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            val noteId = noteRepository.create(note)

            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = null

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val response =
                client.get("/api/v1/notes/$noteId/previews") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.OK, response.status)

            val responseBody = response.bodyAsText()
            val previews = jsonSerializer.decodeFromString<List<URLPreview>>(responseBody)
            assertEquals(0, previews.size)
        }

    @Test
    fun `test get URL previews for non-existent note returns 404`() =
        testApplication {
            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = null

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val response =
                client.get("/api/v1/notes/999999/previews") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `test get URL previews for note with invalid preview data`() =
        testApplication {
            val note =
                Note(
                    id = 0,
                    userId = testUserId,
                    title = "Note with Invalid Preview Data",
                    content = "Content",
                    isPublished = true,
                    previewData = "invalid json",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            val noteId = noteRepository.create(note)

            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = null

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val response =
                client.get("/api/v1/notes/$noteId/previews") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.OK, response.status)

            val responseBody = response.bodyAsText()
            val previews = jsonSerializer.decodeFromString<List<URLPreview>>(responseBody)
            assertEquals(0, previews.size)
        }

    @Test
    fun `test create note with markdown links extracts URLs`() =
        testApplication {
            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = null

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val response =
                client.post("/api/v1/notes") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $accessToken")
                    setBody(
                        NoteCreationRequest(
                            title = "Note with Markdown",
                            content = "Check out [Example](https://example.com) and https://google.com",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Created, response.status)
        }

    @Test
    fun `test create note with empty title uses null`() =
        testApplication {
            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = null

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val response =
                client.post("/api/v1/notes") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $accessToken")
                    setBody(
                        NoteCreationRequest(
                            title = "   ",
                            content = "Content with empty title",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Created, response.status)

            val responseBody = response.bodyAsText()
            val createdNote = jsonSerializer.decodeFromString<Note>(responseBody)
            val noteId = createdNote.id

            val getResponse =
                client.get("/api/v1/notes/$noteId") {
                    header("Authorization", "Bearer $accessToken")
                }
            val note = jsonSerializer.decodeFromString<Note>(getResponse.bodyAsText())
            assertEquals(null, note.title)
        }

    @Test
    fun `test create note with required content only`() =
        testApplication {
            application {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

                // Configure JWT authentication for testing
                install(Authentication) {
                    jwt("jwt-auth") {
                        verifier(
                            JWT.require(Algorithm.HMAC256("test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray()))
                                .build(),
                        )
                        validate { credentials ->
                            val userIdString = credentials.payload.subject
                            val userId =
                                try {
                                    UUID.fromString(userIdString)
                                } catch (e: Exception) {
                                    return@validate null
                                }
                            val user = userRepository.findById(userId)
                            if (user != null) {
                                JWTPrincipal(userId, user.email)
                            } else {
                                null
                            }
                        }
                    }
                }

                com.mel.bubblenotes.api.noteRepository = this@NotesApiIntegrationTest.noteRepository
                com.mel.bubblenotes.api.urlPreviewService = null

                routing {
                    notesApi()
                }
            }

            client =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                            },
                        )
                    }
                }

            val accessToken = createAccessToken(testUserId)

            val response =
                client.post("/api/v1/notes") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $accessToken")
                    setBody(
                        NoteCreationRequest(
                            title = null,
                            content = "Content only note",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Created, response.status)
        }
}
