package com.mel.bubblenotes.services

import io.ktor.client.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.apache.commons.validator.routines.UrlValidator
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * URL Preview data model
 */
@Serializable
data class URLPreview(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val favicon: String? = null,
    val image: String? = null,
    val siteName: String? = null
)

/**
 * Service for fetching and caching URL previews (Open Graph metadata)
 */
open class URLPreviewService(
    private val httpClient: HttpClient = createDefaultHttpClient(),
    private val cacheDurationMs: Long = 24 * 60 * 60 * 1000L // 24 hours
) {
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    // Logger for debugging
    private val logger = LoggerFactory.getLogger(URLPreviewService::class.java)
    
    companion object {
        private const val TIMEOUT_SECONDS = 5L
        private const val MAX_TITLE_LENGTH = 150
        private const val MAX_DESCRIPTION_LENGTH = 200
        
        fun createDefaultHttpClient(): HttpClient {
            return HttpClient(CIO) {
                install(UserAgent) {
                    agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = TIMEOUT_SECONDS * 1000
                    connectTimeoutMillis = TIMEOUT_SECONDS * 1000
                    socketTimeoutMillis = TIMEOUT_SECONDS * 1000
                }
                // Redirect is enabled by default in Ktor 3.x
            }
        }
        
        /**
         * URL detection regex for Markdown content
         * Matches URLs in format: http://..., https://..., or markdown links [text](url)
         */
        val urlPattern = Regex("""https?://[^\s<>"{}|\\^`\[\]]+|www\.[^\s<>"{}|\\^`\[\]]+""")
        
        // Matches markdown links [text](url)
        val markdownLinkPattern = Regex("""\[[^\]]+\]\((https?://[^\s)]+)\)""")
        
        /**
         * Extracts all URLs from Markdown content (both plain URLs and markdown links)
         */
        fun extractURLs(content: String): Set<String> {
            val urls = mutableSetOf<String>()
            
            // 1. Extract URLs from markdown links first
            // Pattern: [text](url)
            markdownLinkPattern.findAll(content).forEach { match ->
                val url = match.groupValues[1]
                urls.add(normalizeURL(url))
            }

        // 2. Extract plain URLs that are NOT part of a markdown link
        // Use Apache Commons Validator to detect valid URLs, handling domains without scheme
        val validator = UrlValidator(arrayOf("http", "https"), UrlValidator.ALLOW_ALL_SCHEMES)
        // Split content by whitespace and common punctuation to get candidate tokens
        val tokens = content.split(Regex("""[\s\[\]\(\)<>\"'`{}|^]+"""))
        tokens.forEach { token ->
            val cleaned = token.trimEnd(',', '.', ';', ':', '!', '?')
            if (cleaned.isEmpty()) return@forEach
            var candidate = cleaned
            // If token lacks scheme, prepend http:// for validation
            if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
                candidate = "http://" + candidate
            }
            if (validator.isValid(candidate)) {
                urls.add(normalizeURL(cleaned))
            }
        }
            
            return urls
        }
        
        /**
         * Normalize URL for consistent caching
         */
        fun normalizeURL(url: String): String {
            return try {
                // Remove fragment first
                val urlWithoutFragment = if (url.contains("#")) url.substringBefore("#") else url
                // Ensure scheme is present for proper URI parsing
                val urlWithScheme = if (urlWithoutFragment.startsWith("http://") || urlWithoutFragment.startsWith("https://")) {
                    urlWithoutFragment
                } else {
                    "http://" + urlWithoutFragment
                }
                val uri = java.net.URI(urlWithScheme).normalize()
                val scheme = uri.scheme?.lowercase() ?: "http"
                val host = uri.host?.lowercase() ?: ""
                var path = uri.path ?: ""
                if (path == "/") path = ""
                if (path.endsWith("/")) path = path.removeSuffix("/")
                val query = if (uri.query != null) "?${uri.query}" else ""
                
                "$scheme://$host$path$query"
            } catch (e: Exception) {
                var res = url.substringBefore("#")
                if (res.endsWith("/") && res.length > 8) res = res.removeSuffix("/")
                res
            }
        }
    }
    
    data class CacheEntry(
        val preview: URLPreview,
        val timestamp: Long
    )
    
    /**
     * Fetch URL preview with caching
     */
    suspend fun fetchPreview(url: String): URLPreview {
        val normalizedUrl = normalizeURL(url)
        logger.info("Fetching preview for URL: {} (normalized: {})", url, normalizedUrl)
        
        // Check cache first
        val cached = cache[normalizedUrl]
        if (cached != null) {
            val now = getCurrentTime()
            if (now - cached.timestamp < cacheDurationMs) {
                logger.info("Cache hit for URL: {}", normalizedUrl)
                return cached.preview
            }
        }
        
        // Fetch with timeout
        val preview = try {
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(TIMEOUT_SECONDS * 1000L) {
                    fetchPreviewFromURL(normalizedUrl)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to fetch preview for {}: {}", normalizedUrl, e.message)
            URLPreview(url = url, title = null, description = null)
        }
        
        // Cache the result
        cache[normalizedUrl] = CacheEntry(preview, getCurrentTime())
        logger.info("Cached preview for URL: {}", normalizedUrl)
        return preview
    }

    /**
     * Get current time, can be overridden for testing
     */
    protected open fun getCurrentTime(): Long {
        return System.currentTimeMillis()
    }
    
    /**
     * Fetch all URL previews from Markdown content
     */
    suspend fun fetchPreviewsFromContent(content: String): List<URLPreview> {
        logger.info("Extracting URLs from content of length {}", content.length)
        val urls = extractURLs(content)
        logger.info("Found {} URLs: {}", urls.size, urls.joinToString())
        val previews = urls.map { url ->
            fetchPreview(url)
        }
        logger.info("Fetched {} URL previews", previews.size)
        return previews
    }
    
    /**
     * Fetch preview metadata from a URL
     */
    private suspend fun fetchPreviewFromURL(url: String): URLPreview {
        logger.info("Fetching URL content for {}", url)
        return try {
            val html = try {
                val response: HttpResponse = httpClient.get(url)
                logger.info("Received response status {} for {}", response.status, url)
                if (response.status != HttpStatusCode.OK) {
                    return URLPreview(url = url)
                }
                response.body<String>()
            } catch (e: Exception) {
                logger.warn("HTTP request failed for {}: {}", url, e.message)
                return URLPreview(url = url)
            }
            
            val document = Jsoup.parse(html, url)
            
            // Extract Open Graph metadata
            val title = (extractOGTag(document, "og:title") ?: document.title()).takeIf { it.isNotBlank() }
            val description = extractOGTag(document, "og:description") ?: extractMetaTag(document, "description")
            val image = extractOGTag(document, "og:image")
            val siteName = extractOGTag(document, "og:site_name")
            
            // Extract favicon
            val favicon = extractFavicon(document, url)
            
            URLPreview(
                url = url,
                title = title?.let { truncateIfNeeded(it, MAX_TITLE_LENGTH) },
                description = description?.let { truncateIfNeeded(it, MAX_DESCRIPTION_LENGTH) },
                favicon = favicon,
                image = image,
                siteName = siteName
            )
        } catch (e: Exception) {
            logger.error("Error processing preview for {}: {}", url, e.message)
            URLPreview(url = url)
        }
    }
    
    /**
     * Extract Open Graph tag value
     */
    private fun extractOGTag(document: Document, tagName: String): String? {
        val element = document.select("meta[property=\"$tagName\"]").firstOrNull() ?:
                      document.select("meta[name=\"$tagName\"]").firstOrNull()
        return element?.attr("content")?.takeIf { it.isNotBlank() }
    }
    
    /**
     * Extract regular meta tag value
     */
    private fun extractMetaTag(document: Document, tagName: String): String? {
        val element = document.select("meta[name=\"$tagName\"]").firstOrNull()
        return element?.attr("content")?.takeIf { it.isNotBlank() }
    }
    
    /**
     * Extract favicon URL from document
     */
    private fun extractFavicon(document: Document, baseUrl: String): String? {
        val faviconLink = document.select("link[rel=icon], link[rel=shortcut icon]").firstOrNull()
        return faviconLink?.attr("href")?.let { faviconPath ->
            try {
                java.net.URI(baseUrl).resolve(faviconPath).toString()
            } catch (e: Exception) {
                faviconPath
            }
        }
    }
    
    /**
     * Truncate string with ellipsis if needed
     */
    private fun truncateIfNeeded(text: String, maxLength: Int): String {
        return if (text.length > maxLength) {
            text.substring(0, maxLength - 3) + "..."
        } else {
            text
        }
    }
    
    /**
     * Clear cache entry
     */
    fun clearCache(url: String) {
        cache.remove(normalizeURL(url))
    }
    
    /**
     * Clear entire cache
     */
    fun clearCache() {
        cache.clear()
    }
}
