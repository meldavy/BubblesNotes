package com.mel.bubblenotes

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import io.ktor.util.*
import java.sql.Connection

class DatabaseService(private val dataSource: HikariDataSource) {
    fun getConnection(): Connection = dataSource.connection
}

fun Application.configureDatabases() {
    // Get database config with fallback to system properties and defaults for testing
    val dbUrl = try {
        environment.config.property("db.url").getString()
    } catch (e: Exception) {
        System.getProperty("db.url") ?: System.getenv("DB_URL") ?: "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
    }
    val dbUser = try {
        environment.config.property("db.user").getString()
    } catch (e: Exception) {
        System.getProperty("db.user") ?: System.getenv("DB_USER") ?: "sa"
    }
    val dbPassword = try {
        environment.config.property("db.password").getString()
    } catch (e: Exception) {
        System.getProperty("db.password") ?: System.getenv("DB_PASSWORD") ?: ""
    }
    
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = dbUrl
        username = dbUser
        password = dbPassword
        maximumPoolSize = 10
        minimumIdle = 2
    }
    
    val dataSource = HikariDataSource(hikariConfig)
    val dbService = DatabaseService(dataSource)
    
    // Store database service in application attributes for use in API routes
    val DB_SERVICE_KEY = AttributeKey<DatabaseService>("DB_SERVICE")
    attributes[DB_SERVICE_KEY] = dbService
}

fun Application.getDatabaseService(): DatabaseService {
    val DB_SERVICE_KEY = AttributeKey<DatabaseService>("DB_SERVICE")
    return attributes[DB_SERVICE_KEY] ?: throw IllegalStateException("DatabaseService not configured in Application")
}
