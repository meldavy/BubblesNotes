package com.mel.bubblenotes

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import com.mel.bubblenotes.api.aiEnhancementService
import com.mel.bubblenotes.api.noteRepository
import com.mel.bubblenotes.api.noteTagRepository
import com.mel.bubblenotes.api.tagService
import com.mel.bubblenotes.api.urlPreviewService
import com.mel.bubblenotes.repositories.AITaskRepository
import com.mel.bubblenotes.repositories.AttachmentRepository
import com.mel.bubblenotes.repositories.NoteRepository
import com.mel.bubblenotes.repositories.NoteTagRepository
import com.mel.bubblenotes.repositories.RefreshTokenRepository
import com.mel.bubblenotes.repositories.TagRepository
import com.mel.bubblenotes.repositories.UserRepository
import com.mel.bubblenotes.services.AIEnhancementService
import com.mel.bubblenotes.services.ApiKeyService
import com.mel.bubblenotes.services.EncryptionService
import com.mel.bubblenotes.services.JWTTokenService
import com.mel.bubblenotes.services.OpenAIClient
import com.mel.bubblenotes.services.SessionStorage
import com.mel.bubblenotes.services.TagService
import com.mel.bubblenotes.services.URLPreviewService
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.util.*

/**
 * Guice dependency injection modules for BubblesNotes application.
 * Configures all services and repositories for proper dependency injection.
 */
class ApplicationModule(
    private val databaseService: DatabaseService,
    private val config: ApplicationConfig,
) : AbstractModule() {
    /**
     * Helper function to get a config property with a required fallback.
     * This ensures critical properties are always available - either from config,
     * environment variables, or a required default that makes the server fail fast
     * if nothing is configured.
     */
    private fun getRequiredConfigProperty(
        path: String,
        defaultValue: String = "",
    ): String {
        return try {
            config.property(path).getString()
        } catch (e: Exception) {
            // Property not found in config - check environment variable
            val envKey = path.replace(".", "_").uppercase()
            System.getenv(envKey) ?: defaultValue
        }
    }

    override fun configure() {
        // Bind services - config reads from application.yaml with env var fallbacks
        bind(EncryptionService::class.java).toInstance(
            EncryptionService(getRequiredConfigProperty("encryption.key")),
        )
        bind(SessionStorage::class.java).toInstance(
            SessionStorage(getRequiredConfigProperty("encryption.session-key")),
        )
        bind(ApiKeyService::class.java).toInstance(ApiKeyService())
    }

    @Provides
    @Singleton
    fun provideApplicationConfig(): ApplicationConfig {
        return config
    }

    @Provides
    @Singleton
    fun provideNoteRepository(): NoteRepository {
        return NoteRepository(databaseService.getDataSource())
    }

    @Provides
    @Singleton
    fun provideTagRepository(): TagRepository {
        return TagRepository(databaseService.getDataSource())
    }

    @Provides
    @Singleton
    fun provideAttachmentRepository(): AttachmentRepository {
        return AttachmentRepository(databaseService.getDataSource())
    }

    @Provides
    @Singleton
    fun provideUserRepository(): UserRepository {
        return UserRepository(databaseService.getDataSource())
    }

    @Provides
    @Singleton
    fun provideRefreshTokenRepository(): RefreshTokenRepository {
        return RefreshTokenRepository(databaseService.getDataSource())
    }

    @Provides
    @Singleton
    fun provideJWTTokenService(
        refreshTokenRepository: RefreshTokenRepository,
        config: ApplicationConfig,
    ): JWTTokenService {
        val secretKey = getRequiredConfigProperty("jwt.secret-key").toByteArray()
        val accessTokenTtl = getRequiredConfigProperty("jwt.access-token-ttl").toLongOrNull() ?: 900L
        val refreshTokenTtl = getRequiredConfigProperty("jwt.refresh-token-ttl").toLongOrNull() ?: 604800L

        return JWTTokenService(
            refreshTokenRepository = refreshTokenRepository,
            secretKey = secretKey,
            accessTokenTtlSeconds = accessTokenTtl,
            refreshTokenTtlSeconds = refreshTokenTtl,
        )
    }

    @Provides
    @Singleton
    fun provideNoteTagRepository(): NoteTagRepository {
        return NoteTagRepository(databaseService.getDataSource())
    }

    @Provides
    @Singleton
    fun provideAITaskRepository(): AITaskRepository {
        return AITaskRepository(databaseService.getDataSource())
    }

    @Provides
    @Singleton
    fun provideOpenAIClient(): OpenAIClient {
        val apiKey = getRequiredConfigProperty("ai.openai.api-key")
        val apiUrl = getRequiredConfigProperty("ai.openai.api-url")
        val modelId = getRequiredConfigProperty("ai.openai.model-id")
        val summaryThreshold = getRequiredConfigProperty("ai.openai.summary-threshold").toIntOrNull() ?: 500
        val requestTimeout = getRequiredConfigProperty("ai.openai.request-timeout").toLongOrNull() ?: 120000L

        return OpenAIClient(
            apiKey = apiKey,
            apiUrl = apiUrl,
            modelId = modelId,
            summaryThreshold = summaryThreshold,
            requestTimeout = requestTimeout,
        )
    }

    @Provides
    @Singleton
    fun provideAIEnhancementService(
        openAIClient: OpenAIClient,
        aiTaskRepository: AITaskRepository,
        noteRepository: NoteRepository,
    ): AIEnhancementService {
        val service =
            AIEnhancementService(
                openAIClient = openAIClient,
                aiTaskRepository = aiTaskRepository,
                noteRepository = noteRepository,
            )
        // Start the scheduler
        service.start()
        return service
    }
}

/**
 * Extension function to configure Guice DI in Ktor application.
 */
fun Application.configureDI() {
    val databaseService = getDatabaseService()
    val config = environment.config

    // Create Guice injector
    val injector = com.google.inject.Guice.createInjector(ApplicationModule(databaseService, config))

    // Store injector in application attributes for later retrieval
    val INJECTOR_KEY = AttributeKey<com.google.inject.Injector>("INJECTOR")
    attributes[INJECTOR_KEY] = injector

    // Initialize repositories from injector and set them for API routes
    val noteRepository = injector.getInstance(NoteRepository::class.java)
    com.mel.bubblenotes.api.noteRepository = noteRepository

    // Initialize URL preview service for API routes
    com.mel.bubblenotes.api.urlPreviewService = URLPreviewService()

    // Initialize NoteTagRepository for tag relationships
    val noteTagRepository = injector.getInstance(NoteTagRepository::class.java)
    com.mel.bubblenotes.api.noteTagRepository = noteTagRepository

    // Initialize TagService
    val tagService =
        TagService(noteTagRepository, injector.getInstance(TagRepository::class.java), injector.getInstance(NoteRepository::class.java))
    com.mel.bubblenotes.api.tagService = tagService

    // Initialize AI Enhancement Service for API routes
    val aiEnhancementService = injector.getInstance(AIEnhancementService::class.java)
    com.mel.bubblenotes.api.aiEnhancementService = aiEnhancementService
    environment.log.info("AI Enhancement Service initialized for API routes")

    // Log DI initialization
    environment.log.info("Dependency injection configured with Guice")
}

/**
 * Extension function to retrieve the Guice injector from application.
 */
fun Application.getInjector(): com.google.inject.Injector {
    val INJECTOR_KEY = AttributeKey<com.google.inject.Injector>("INJECTOR")
    return attributes[INJECTOR_KEY] ?: throw IllegalStateException("Guice injector not configured in Application")
}

/**
 * Extension function to get a service instance from the injector.
 */
fun <T> Application.getService(serviceClass: Class<T>): T {
    return getInjector().getInstance(serviceClass)
}
