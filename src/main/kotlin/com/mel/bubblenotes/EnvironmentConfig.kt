package com.mel.bubblenotes

import io.ktor.server.application.*
import io.ktor.util.*

/**
 * List of sensitive environment variable patterns that should be redacted in logs.
 * These patterns are matched case-insensitively against variable names.
 */
private val SENSITIVE_PATTERNS =
    listOf(
        "KEY",
        "SECRET",
        "PASSWORD",
        "TOKEN",
        "CREDENTIAL",
    )

/**
 * Maps configuration keys to their environment variable names for logging purposes.
 * This provides visibility into critical configuration at startup.
 */
private val CONFIG_TO_ENV_MAP =
    mapOf(
        "ktor.deployment.port" to "PORT",
        "db.url" to "DB_URL",
        "db.user" to "DB_USER",
        "db.password" to "DB_PASSWORD",
        "db.pool-size" to "DB_POOL_SIZE",
        "db.min-idle" to "DB_MIN_IDLE",
        "encryption.key" to "ENCRYPTION_KEY",
        "encryption.session-key" to "SESSION_ENCRYPTION_KEY",
        "oauth.google.client-id" to "GOOGLE_CLIENT_ID",
        "oauth.google.client-secret" to "GOOGLE_CLIENT_SECRET",
        "oauth.google.redirect-uri" to "GOOGLE_REDIRECT_URI",
        "ai.openai.api-key" to "OPENAI_API_KEY",
        "ai.openai.api-url" to "OPENAI_API_URL",
        "ai.openai.model-id" to "OPENAI_MODEL_ID",
        "ai.openai.summary-threshold" to "AI_SUMMARY_THRESHOLD",
        "ai.openai.request-timeout" to "AI_REQUEST_TIMEOUT",
        // CORS allowed origin (full URL)
        "origin" to "ORIGIN",
    )

/**
 * Determines if a configuration key or environment variable name contains sensitive information.
 * @param key The configuration key or environment variable name to check
 * @return true if the key matches a sensitive pattern
 */
private fun isSensitive(key: String): Boolean {
    val upperKey = key.uppercase()
    return SENSITIVE_PATTERNS.any { pattern -> upperKey.contains(pattern) }
}

/**
 * Redacts a sensitive value for safe logging.
 * Shows only the first 3 characters and masks the rest.
 * @param value The value to redact
 * @return The redacted string, or "*** (not set)" if empty
 */
private fun redactValue(value: String): String {
    if (value.isBlank()) {
        return "*** (not set)"
    }
    return if (value.length <= 3) {
        "***"
    } else {
        "${value.take(3)}***"
    }
}

/**
 * Logs all critical environment variables at startup for debugging and visibility.
 * Sensitive values (API keys, passwords, secrets) are redacted.
 * Also warns about any critical variables that are missing or using default values.
 */
fun Application.logEnvironmentVariables() {
    val log = environment.log
    log.info("========================================")
    log.info("  BubblesNotes Service Startup")
    log.info("  Environment Configuration Summary")
    log.info("========================================")

    val config = environment.config

    // Track if any critical configuration is missing
    val missingCriticalVars = mutableListOf<String>()
    val usingDefaults = mutableListOf<String>()

    // Log each critical configuration value
    CONFIG_TO_ENV_MAP.forEach { (configKey, envVarName) ->
        val value =
            try {
                config.property(configKey).getString()
            } catch (e: Exception) {
                null
            }

        val displayValue =
            when {
                value == null -> "*** (config error)"
                isSensitive(configKey) -> redactValue(value)
                else -> value
            }

        log.info("  $envVarName: $displayValue")

        // Check for missing critical values
        if (value.isNullOrBlank()) {
            // Check if this is a truly critical variable (not just optional)
            when (configKey) {
                "oauth.google.client-id", "oauth.google.client-secret" -> {
                    missingCriticalVars.add(envVarName)
                }
                "ai.openai.api-key" -> {
                    missingCriticalVars.add(envVarName)
                }
                // Require explicit origin in production; default exists for dev but warn if empty/misconfigured
                "origin" -> {
                    missingCriticalVars.add(envVarName)
                }
                "encryption.key", "encryption.session-key" -> {
                    // Check if using dev defaults
                    if (value?.contains("dev-") == true || value?.contains("!!") == true) {
                        usingDefaults.add(envVarName)
                    }
                }
            }
        }

        // Check for dev defaults
        if (value != null) {
            when {
                value.contains("dev-encryption-key-do-not-use-in-production") -> {
                    usingDefaults.add("$envVarName (using insecure dev default)")
                }
                value.contains("dev-session-key-48bytes-for-aes-gcm") -> {
                    usingDefaults.add("$envVarName (using insecure dev default)")
                }
                value == "jdbc:h2:mem:bubblesnotes" -> {
                    usingDefaults.add("$envVarName (using H2 in-memory database)")
                }
            }
        }
    }

    // Log warnings for missing or default configurations
    if (missingCriticalVars.isNotEmpty() || usingDefaults.isNotEmpty()) {
        log.warn("========================================")
        log.warn("  Configuration Warnings")
        log.warn("========================================")

        if (missingCriticalVars.isNotEmpty()) {
            log.warn("  Missing critical variables:")
            missingCriticalVars.forEach { varName ->
                log.warn("    - $varName is not configured")
            }
        }

        if (usingDefaults.isNotEmpty()) {
            log.warn("  Using default/insecure values:")
            usingDefaults.forEach { warning ->
                log.warn("    - $warning")
            }
        }

        log.warn("  Please configure these variables before production deployment.")
    }

    log.info("========================================")
    log.info("  Environment check complete")
    log.info("========================================")
}
