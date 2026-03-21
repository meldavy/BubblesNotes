package com.mel.bubblenotes

import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    logEnvironmentVariables() // Log environment config early for startup visibility
    configureMonitoring()
    configureSerialization()
    configureDatabases()
    configureDI() // Configure dependency injection after databases
    configureSecurity() // Move security after DI so we can inject userRepository
    configureSockets()
    configureRouting()
}
