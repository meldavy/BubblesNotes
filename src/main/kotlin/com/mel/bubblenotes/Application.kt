package com.mel.bubblenotes

import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSecurity()
    configureMonitoring()
    configureSerialization()
    configureDatabases()
    configureDI()  // Configure dependency injection after databases
    configureSockets()
    configureRouting()
}
