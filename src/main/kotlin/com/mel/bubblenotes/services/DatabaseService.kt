package com.mel.bubblenotes.services

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import javax.sql.DataSource
import java.sql.Connection

class DatabaseService(private val application: Application) {
    private var dataSource: DataSource? = null

    fun getDataSource(): DataSource {
        return dataSource ?: synchronized(this) {
            dataSource ?: createDataSource().also { dataSource = it }
        }
    }

    fun getConnection(): Connection {
        return getDataSource().connection
    }

    private fun createDataSource(): DataSource {
        val configValue = application.environment.config.property("ktor.deployment.embedded").getString()
        val embedded = if (configValue.isEmpty()) true else configValue.toBoolean()
        
        if (embedded) {
            // Use H2 for testing/development with shared cache mode
            // The 'mem:' prefix with a database name allows multiple connections to share the same in-memory database
            val config = HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:bubblesnotes;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;CACHE_SIZE_KB=1024"
                driverClassName = "org.h2.Driver"
                username = "root"
                password = ""
                maximumPoolSize = 5
            }
            return HikariDataSource(config)
        } else {
            // Use PostgreSQL for production
            val config = HikariConfig().apply {
                jdbcUrl = application.environment.config.property("postgres.url").getString()
                username = application.environment.config.property("postgres.user").getString()
                password = application.environment.config.property("postgres.password").getString()
                maximumPoolSize = 10
            }
            return HikariDataSource(config)
        }
    }
}
