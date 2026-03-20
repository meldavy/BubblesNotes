package com.mel.bubblenotes

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import com.mel.bubblenotes.repositories.AttachmentRepository
import com.mel.bubblenotes.repositories.NoteRepository
import com.mel.bubblenotes.repositories.TagRepository
import com.mel.bubblenotes.repositories.UserRepository
import com.mel.bubblenotes.repositories.NoteTagRepository
import com.mel.bubblenotes.services.ApiKeyService
import com.mel.bubblenotes.services.EncryptionService
import com.mel.bubblenotes.services.SessionStorage
import com.mel.bubblenotes.services.URLPreviewService
import com.mel.bubblenotes.services.TagService
import io.ktor.server.application.*
import io.ktor.util.*

/**
 * Guice dependency injection modules for BubblesNotes application.
 * Configures all services and repositories for proper dependency injection.
 */
class ApplicationModule(private val databaseService: DatabaseService) : AbstractModule() {
    override fun configure() {
        // Bind services
        bind(EncryptionService::class.java).toInstance(
            EncryptionService(System.getenv("ENCRYPTION_KEY") ?: "default-key-change-in-production")
        )
        bind(SessionStorage::class.java).toInstance(
            SessionStorage(System.getenv("SESSION_ENCRYPTION_KEY") ?: "default-session-key-change-in-production")
        )
        bind(ApiKeyService::class.java).toInstance(ApiKeyService())
    }

    @Provides
    @Singleton
    fun provideNoteRepository(): NoteRepository {
        return NoteRepository(databaseService.getConnection())
    }

    @Provides
    @Singleton
    fun provideTagRepository(): TagRepository {
        return TagRepository(databaseService.getConnection())
    }

    @Provides
    @Singleton
    fun provideAttachmentRepository(): AttachmentRepository {
        return AttachmentRepository(databaseService.getConnection())
    }

    @Provides
    @Singleton
    fun provideUserRepository(): UserRepository {
        return UserRepository(databaseService.getConnection())
    }

    @Provides
    @Singleton
    fun provideNoteTagRepository(): NoteTagRepository {
        return NoteTagRepository(databaseService.getConnection())
    }
    }

/**
 * Extension function to configure Guice DI in Ktor application.
 */
fun Application.configureDI() {
    val databaseService = getDatabaseService()

    // Create Guice injector
    val injector = com.google.inject.Guice.createInjector(ApplicationModule(databaseService))

    // Store injector in application attributes for later retrieval
    val INJECTOR_KEY = AttributeKey<com.google.inject.Injector>("INJECTOR")
    attributes[INJECTOR_KEY] = injector

    // Initialize repositories from injector and set them for API routes
    val noteRepository = injector.getInstance(NoteRepository::class.java)
    com.mel.bubblenotes.api.noteRepository = noteRepository
    
    // Initialize URL preview service for API routes
    com.mel.bubblenotes.api.urlPreviewService = URLPreviewService()

    // Initialize NoteTagRepository for tag relationships
    val noteTagRepository = injector.getInstance(com.mel.bubblenotes.repositories.NoteTagRepository::class.java)
    com.mel.bubblenotes.api.noteTagRepository = noteTagRepository

    // Initialize TagService
    val tagService = com.mel.bubblenotes.services.TagService(noteTagRepository, injector.getInstance(com.mel.bubblenotes.repositories.TagRepository::class.java), injector.getInstance(com.mel.bubblenotes.repositories.NoteRepository::class.java))
    com.mel.bubblenotes.api.tagService = tagService

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
