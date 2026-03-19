val h2_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val postgres_version: String by project
val ktor_version: String by project

plugins {
    kotlin("jvm") version "2.3.0"
    id("io.ktor.plugin") version "3.4.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
}

group = "com.mel.bubblenotes"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(17)
}

// Frontend build tasks - runs npm commands directly
tasks.register<Exec>("installFrontend") {
    description = "Install frontend dependencies"
    group = "frontend"
    workingDir = File(project.projectDir, "frontend")
    // Use cmd.exe to run npm on Windows
    commandLine("cmd", "/c", "npm", "install")
}

tasks.register<Exec>("buildFrontend") {
    description = "Build React frontend"
    group = "frontend"
    dependsOn("installFrontend")
    workingDir = File(project.projectDir, "frontend")
    commandLine("cmd", "/c", "npm", "run", "build")
}

// Hook buildFrontend into the main build task
tasks.named("build") {
    dependsOn("buildFrontend")
}

// Copy built frontend to static resources for Ktor
// This runs after buildFrontend and copies to src/main/resources/static
tasks.register<Copy>("copyFrontendToStatic") {
    description = "Copy built React frontend to static resources"
    group = "frontend"
    dependsOn("buildFrontend")
    from(File(project.projectDir, "frontend/build"))
    into(File(project.projectDir, "src/main/resources/static"))
}

// Hook copyFrontendToStatic before processResources so it gets included
tasks.named("processResources") {
    dependsOn("copyFrontendToStatic")
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-resources:$ktor_version")
    implementation("io.ktor:ktor-server-host-common:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-jackson:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("org.postgresql:postgresql:$postgres_version")
    implementation("com.h2database:h2:$h2_version")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("io.ktor:ktor-server-websockets:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml:$ktor_version")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

tasks.test {
    systemProperty("db.url", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
    systemProperty("db.user", "sa")
    systemProperty("db.password", "")
}
