package com.mel.bubblenotes

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import io.ktor.server.application.ApplicationStopped
import io.ktor.util.*
import org.flywaydb.core.Flyway
import java.sql.Connection
import kotlin.concurrent.fixedRateTimer

class DatabaseService(private val dataSource: HikariDataSource) {
    /**
     * Returns the underlying HikariDataSource.
     * Repositories should use this to get fresh connections from the pool for each operation.
     */
    fun getDataSource(): HikariDataSource = dataSource

    /**
     * @deprecated Use getDataSource() instead and get a fresh connection for each operation.
     * Storing a single connection across requests causes "Connection is closed" errors because
     * HikariCP may close idle connections after maxLifetime or idleTimeout.
     */
    @Deprecated("Use getDataSource() instead", ReplaceWith("getDataSource().connection"))
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
            // Connection validation - use SELECT 1 to test connections before borrowing
            // This is more reliable than JDBC 4's isValid() for some PostgreSQL drivers
            connectionTestQuery = "SELECT 1"
            // Leak detection - log warning if connection held longer than this (0 = disabled)
            // Set to 0 in production, enable in development for debugging
            leakDetectionThreshold =
                try {
                    config.property("db.leak-detection-threshold").getString().toLong()
                } catch (e: Exception) {
                    0L
                }
            // Max lifetime of a connection (4 minutes) - MUST be less than DB server timeout
            // Cloud PostgreSQL (AWS RDS, Heroku, etc.) typically has 5-10 minute idle timeout
            // Setting to 4 minutes (240000ms) ensures we close before server does
            maxLifetime = 240000L
            // Idle timeout (3 minutes) - must be less than maxLifetime
            idleTimeout = 180000L
            // Keepalive to prevent aggressive NAT/LB idle drops (must be < idleTimeout and maxLifetime)
            keepaliveTime =
                try {
                    config.property("db.keepalive-time").getString().toLong()
                } catch (e: Exception) {
                    120000L
                }
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

    // Lightweight Hikari metrics logger (disabled if MXBean is unavailable)
    runCatching {
        val mxBean = dataSource.hikariPoolMXBean
        if (mxBean != null) {
            val timer =
                fixedRateTimer(name = "hikari-metrics-logger", daemon = true, initialDelay = 10000L, period = 15000L) {
                    environment.log.info(
                        "Hikari metrics — active=${mxBean.activeConnections}, idle=${mxBean.idleConnections}, pending=${mxBean.threadsAwaitingConnection}, total=${mxBean.totalConnections}",
                    )
                }
            // Stop logging when application stops
            environment.monitor.subscribe(ApplicationStopped) {
                timer.cancel()
            }
        } else {
            environment.log.info("Hikari MXBean not available; pool metrics logger disabled")
        }
    }.onFailure {
        environment.log.warn("Failed to start Hikari metrics logger: ${it.message}")
    }

    // Store database service in application attributes for use in API routes
    val DB_SERVICE_KEY = AttributeKey<DatabaseService>("DB_SERVICE")
    attributes[DB_SERVICE_KEY] = dbService
}

fun Application.getDatabaseService(): DatabaseService {
    val DB_SERVICE_KEY = AttributeKey<DatabaseService>("DB_SERVICE")
    return attributes[DB_SERVICE_KEY] ?: throw IllegalStateException("DatabaseService not configured in Application")
}
