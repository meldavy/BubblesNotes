package com.mel.bubblenotes.api

import com.mel.bubblenotes.JWTPrincipal
import com.mel.bubblenotes.baseStorageDir
import com.mel.bubblenotes.models.FileAttachment
import com.mel.bubblenotes.services.EncryptionService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import java.io.File

// EncryptionService instance - set during DI initialization
var encryptionService: EncryptionService? = null

// FileAttachmentService instance - set during DI initialization
var fileAttachmentService: com.mel.bubblenotes.services.FileAttachmentService? = null

fun Route.fileAttachmentRoutes() {
    authenticate("jwt-auth") {
        // File upload endpoint - supports images and general file attachments
        route("/api/v1/attachments") {
            // Upload a file (independent of any note)
            post {
                val userId =
                    call.principal<JWTPrincipal>()?.userId
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Not authenticated"))

                val service =
                    fileAttachmentService
                        ?: return@post call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            mapOf("error" to "File attachment service not available"),
                        )

                try {
                    val multipartData = call.receiveMultipart()
                    var tempFile: File? = null
                    var fileName: String? = null
                    var contentType: String? = null

                    multipartData.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                fileName = part.originalFileName ?: "file"
                                contentType = part.contentType?.toString() ?: "application/octet-stream"

                                // Create temp file and write content
                                tempFile = File.createTempFile("file-upload-", fileName)
                                part.provider().copyAndClose(tempFile!!.writeChannel())
                            }
                            is PartData.FormItem -> {
                                // Ignore form fields
                            }
                            else -> {}
                        }
                        part.dispose()
                    }

                    if (tempFile == null || fileName == null) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "No file provided"),
                        )
                    }

                    try {
                        // Upload file and get storage path
                        val storagePath =
                            service.uploadFile(
                                file = tempFile!!,
                                userId = userId,
                                fileName = fileName!!,
                                contentType = contentType!!,
                            )

                        call.respond(
                            HttpStatusCode.Created,
                            mapOf(
                                "url" to "/api/v1/attachments/download?path=$storagePath",
                                "fileName" to fileName,
                                "contentType" to (contentType ?: "application/octet-stream"),
                                "isImage" to (contentType?.startsWith("image/") ?: false),
                                "markdown" to
                                    FileAttachment.getMarkdownSyntax(
                                        fileName!!,
                                        "/api/v1/attachments/download?path=$storagePath",
                                        contentType?.startsWith("image/") ?: false,
                                    ),
                            ),
                        )
                    } finally {
                        // Clean up temp file
                        tempFile?.delete()
                    }
                } catch (e: IllegalArgumentException) {
                    // Validation error
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to e.message),
                    )
                } catch (e: Exception) {
                    call.application.log.error("File upload failed: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Upload failed", "message" to e.message),
                    )
                }
            }

            // Download a file by storage path
            get("/download") {
                val storagePath =
                    call.request.queryParameters["path"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing path parameter"))

                val userId =
                    call.principal<JWTPrincipal>()?.userId
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Not authenticated"))

                // DEBUG logging for authorization check
                call.application.log.info("Download endpoint - storagePath: $storagePath, userId: $userId")

                val encService =
                    encryptionService
                        ?: return@get call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            mapOf("error" to "Encryption service not available"),
                        )

                // Verify the user has access to this file by checking path ownership
                // Path format: {userId}/{uuid}.{extension}
                val expectedPrefix = "$userId/"
                call.application.log.info(
                    "Download endpoint - expectedPrefix: $expectedPrefix, storagePath starts with prefix: ${storagePath.startsWith(
                        expectedPrefix,
                    )}",
                )
                if (!storagePath.startsWith(expectedPrefix)) {
                    call.application.log.error(
                        "Download endpoint - ACCESS DENIED: storagePath '$storagePath' does not start with expected prefix '$expectedPrefix'",
                    )
                    return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                }
                call.application.log.info("Download endpoint - ACCESS GRANTED: user $userId has access to $storagePath")

                // Read and decrypt the file
                val fullPath = File(baseStorageDir, storagePath)
                call.application.log.info("Download endpoint - Looking for file at: ${fullPath.absolutePath}, exists: ${fullPath.exists()}")
                if (!fullPath.exists()) {
                    return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "File not found"))
                }

                val encryptedData = fullPath.readBytes()
                val decryptedData = encService.decrypt(encryptedData)

                // Determine content type from file extension if not already known
                val contentType =
                    when (storagePath.substringAfterLast(".").lowercase()) {
                        "png" -> ContentType.Image.PNG
                        "jpg", "jpeg" -> ContentType.Image.JPEG
                        "gif" -> ContentType.Image.GIF
                        "webp" -> ContentType.parse("image/webp")
                        "pdf" -> ContentType.Application.Pdf
                        "txt" -> ContentType.Text.Plain
                        "csv" -> ContentType.parse("text/csv")
                        "zip" -> ContentType.Application.Zip
                        else -> ContentType.Application.OctetStream
                    }

                // Return the file with appropriate content type
                call.respondBytes(
                    bytes = decryptedData,
                    contentType = contentType,
                )
            }
        }
    }
}
