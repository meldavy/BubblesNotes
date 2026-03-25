package com.mel.bubblenotes.api

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.mel.bubblenotes.JWTPrincipal
import com.mel.bubblenotes.repositories.FileAttachmentRepository
import com.mel.bubblenotes.repositories.RefreshTokenRepository
import com.mel.bubblenotes.repositories.UserRepository
import com.mel.bubblenotes.services.EncryptionService
import com.mel.bubblenotes.services.FileAttachmentService
import com.mel.bubblenotes.services.JWTTokenService
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
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FileAttachmentApiIntegrationTest {
    private lateinit var dataSource: HikariDataSource
    private lateinit var userRepository: UserRepository
    private lateinit var fileAttachmentRepository: FileAttachmentRepository
    private lateinit var jwtTokenService: JWTTokenService
    private lateinit var encryptionService: EncryptionService
    private lateinit var fileAttachmentService: FileAttachmentService
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
        // Setup H2 in-memory database
        val hikariConfig =
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
                username = "sa"
                password = ""
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
            stmt.execute("DELETE FROM file_attachments")
            stmt.execute("DELETE FROM api_tokens")
            stmt.execute("DELETE FROM tags")
            stmt.execute("DELETE FROM note_tags")
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
        userRepository = UserRepository(dataSource)
        fileAttachmentRepository = FileAttachmentRepository(dataSource)
        val refreshTokenRepository = RefreshTokenRepository(dataSource)
        jwtTokenService =
            JWTTokenService(
                refreshTokenRepository = refreshTokenRepository,
                secretKey = "test-jwt-secret-key-for-testing-only-32bytes!!!!".toByteArray(),
                accessTokenTtlSeconds = 3600,
                refreshTokenTtlSeconds = 604800,
            )
        encryptionService = EncryptionService("test-encryption-key-for-testing-only")
        fileAttachmentService = FileAttachmentService(fileAttachmentRepository, encryptionService)
    }

    @AfterTest
    fun teardown() {
        dataSource.close()
    }

    @Test
    fun `test upload file without authentication returns 401`() =
        testApplication {
            application {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

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

                com.mel.bubblenotes.api.fileAttachmentService = fileAttachmentService
                com.mel.bubblenotes.api.encryptionService = encryptionService

                routing {
                    fileAttachmentRoutes()
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

            val response =
                client.post("/api/v1/attachments") {
                    // No authorization header
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `test download non-existent file returns 404`() =
        testApplication {
            application {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }

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

                com.mel.bubblenotes.api.fileAttachmentService = fileAttachmentService
                com.mel.bubblenotes.api.encryptionService = encryptionService

                routing {
                    fileAttachmentRoutes()
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
                client.get("/api/v1/attachments/download?path=$testUserId/nonexistent.png") {
                    header("Authorization", "Bearer $accessToken")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
}
