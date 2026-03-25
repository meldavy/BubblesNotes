package com.mel.bubblenotes.models

import com.mel.bubblenotes.serializers.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a file attachment associated with a note.
 * Supports both images (PNG, JPEG, GIF, WebP, etc.) and general file attachments
 * (PDF, DOC, DOCX, TXT, CSV, ZIP, code files, archives, etc.).
 * Each upload operation creates a new FileAttachment record.
 */
@Serializable
data class FileAttachment(
    val id: Long,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val fileName: String,
    val contentType: String,
    val fileSize: Long,
    val storagePath: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val MAX_FILE_SIZE = 10_485_760L // 10MB in bytes

        /**
         * Allowed MIME type categories for file attachments.
         * Uses prefix matching to support all common file types within each category.
         */
        val ALLOWED_MIME_PREFIXES =
            setOf(
                // Images - all image types
                "image/",
                // Documents
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-excel",
                "application/vnd.ms-powerpoint",
                // Text files - all text types
                "text/",
                // Archives
                "application/zip",
                "application/x-rar-compressed",
                "application/x-7z-compressed",
                "application/gzip",
                "application/x-tar",
                "application/x-gzip",
                // Code files (specific text-based code types)
                "application/javascript",
                "application/json",
                "application/xml",
                "text/x-python",
                "text/x-java-source",
                "text/x-shellscript",
                "text/html",
                "text/css",
                // Generic binary (safe fallback)
                "application/octet-stream",
            )

        /**
         * Blocked MIME types for security reasons.
         * These are potentially dangerous file types that should not be uploaded.
         */
        val BLOCKED_MIME_PREFIXES =
            setOf(
                // Executables
                "application/x-executable",
                "application/x-msdownload",
                "application/x-msdos-program",
                "application/x-sh",
                "application/x-perl",
                "application/x-php",
                "application/x-python",
                "application/java-archive",
                "application/x-java-applet",
                "application/x-java-jnlp-file",
                // Scripts
                "application/x-script",
                "text/x-script",
                // Potentially dangerous document types
                "application/x-swf",
                "application/x-shockwave-flash",
            )

        /**
         * Checks if a content type is allowed based on MIME type categories.
         * Uses prefix matching for broad support while maintaining security.
         */
        fun isAllowedContentType(contentType: String): Boolean {
            val normalizedType = contentType.lowercase().trim()

            // First check if it's explicitly blocked
            if (BLOCKED_MIME_PREFIXES.any { normalizedType.startsWith(it) }) {
                return false
            }

            // Then check if it matches an allowed prefix
            return ALLOWED_MIME_PREFIXES.any { normalizedType.startsWith(it) }
        }

        /**
         * Gets the appropriate markdown syntax for this file type.
         * - Images: ![fileName](url)
         * - Files: [fileName](url)
         */
        fun getMarkdownSyntax(
            fileName: String,
            url: String,
            isImage: Boolean,
        ): String {
            return if (isImage) {
                "![$fileName]($url)"
            } else {
                "[$fileName]($url)"
            }
        }
    }

    init {
        require(fileSize <= MAX_FILE_SIZE) {
            "File size exceeds 10MB limit"
        }
        require(isAllowedContentType(contentType)) {
            "Unsupported file type: $contentType"
        }
    }

    /**
     * Returns true if this attachment is an image.
     */
    val isImage: Boolean get() = contentType.startsWith("image/")

    /**
     * Returns true if this attachment is a document.
     */
    val isDocument: Boolean get() =
        contentType.startsWith("application/pdf") ||
            contentType.startsWith("application/msword") ||
            contentType.startsWith("application/vnd.openxmlformats-officedocument") ||
            contentType.startsWith("application/vnd.ms-")

    /**
     * Returns true if this attachment is a text file.
     */
    val isText: Boolean get() = contentType.startsWith("text/")

    /**
     * Returns true if this attachment is an archive.
     */
    val isArchive: Boolean get() =
        contentType.startsWith("application/zip") ||
            contentType.startsWith("application/x-rar") ||
            contentType.startsWith("application/x-7z") ||
            contentType.startsWith("application/gzip") ||
            contentType.startsWith("application/x-tar")
}
