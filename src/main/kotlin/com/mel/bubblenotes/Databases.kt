package com.mel.bubblenotes

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import io.ktor.util.*
import org.flywaydb.core.Flyway
import java.sql.Connection

class DatabaseService(private val dataSource: HikariDataSource) {
    fun getConnection(): Connection = dataSource.connection
}

fun Application.configureDatabases() {
    val config = environment.config

    // Get database config from application.yaml with fallbacks for testing
    // Environment variables can override via ${ENV_VAR:default} syntax in YAML
    val dbUrl =
        try {
            config.property("db.url").getString()
        } catch (e: Exception) {
            // Fallback for testing when config is not available
            environment.log.warn("db.url not found in config, using H2 in-memory database for testing")
            "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        }
    val dbUser =
        try {
            config.property("db.user").getString()
        } catch (e: Exception) {
            "sa"
        }
    val dbPassword =
        try {
            config.property("db.password").getString()
        } catch (e: Exception) {
            ""
        }
    val dbPoolSize =
        try {
            config.property("db.pool-size").getString().toIntOrNull() ?: 10
        } catch (e: Exception) {
            10
        }
    val dbMinIdle =
        try {
            config.property("db.min-idle").getString().toIntOrNull() ?: 2
        } catch (e: Exception) {
            2
        }

    val hikariConfig =
        HikariConfig().apply {
            jdbcUrl = dbUrl
            username = dbUser
            password = dbPassword
            maximumPoolSize = dbPoolSize
            minimumIdle = dbMinIdle
            // Connection validation - test query before returning connection from pool
            connectionTestQuery = "SELECT 1"
            // Max lifetime of a connection (30 minutes) - should be less than DB server timeout
            maxLifetime = 1800000L
            // Idle timeout (10 minutes) - remove idle connections proactively
            idleTimeout = 600000L
            // Connection timeout (30 seconds) - fail fast if pool is exhausted
            connectionTimeout = 30000L
        }

    val dataSource = HikariDataSource(hikariConfig)

    // Run Flyway migrations
    try {
        val flyway =
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
        flyway.migrate()
        environment.log.info("Database migrations completed successfully")
    } catch (e: Exception) {
        environment.log.error("Failed to run database migrations: ${e.message}")
        throw e
    }

    val dbService = DatabaseService(dataSource)

    // Store database service in application attributes for use in API routes
    val DB_SERVICE_KEY = AttributeKey<DatabaseService>("DB_SERVICE")
    attributes[DB_SERVICE_KEY] = dbService
}

fun Application.getDatabaseService(): DatabaseService {
    val DB_SERVICE_KEY = AttributeKey<DatabaseService>("DB_SERVICE")
    return attributes[DB_SERVICE_KEY] ?: throw IllegalStateException("DatabaseService not configured in Application")
}
