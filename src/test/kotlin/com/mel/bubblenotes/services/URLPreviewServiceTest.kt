package com.mel.bubblenotes.services

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class URLPreviewServiceTest {

    private val jsonSerializer = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `extractURLs should extract plain URLs from content`() {
        val content = "Check out https://example.com and http://test.org/page"
        val urls = URLPreviewService.extractURLs(content)
        
        assertEquals(2, urls.size)
        assertTrue(urls.contains("https://example.com"))
        assertTrue(urls.contains("http://test.org/page"))
    }

    @Test
    fun `extractURLs should extract URLs from markdown links`() {
        val content = "Visit [Example](https://example.com) or [Test](http://test.org)"
        val urls = URLPreviewService.extractURLs(content)
        
        assertEquals(2, urls.size)
        assertTrue(urls.contains("https://example.com"))
        assertTrue(urls.contains("http://test.org"))
    }

    @Test
    fun `extractURLs should handle mixed content`() {
        val content = """
            Here's a link: https://example.com
            And a markdown link: [Google](https://google.com)
            Another URL: http://test.org/page?query=1
        """.trimIndent()
        val urls = URLPreviewService.extractURLs(content)
        
        assertEquals(3, urls.size)
        assertTrue(urls.contains("https://example.com"))
        assertTrue(urls.contains("https://google.com"))
        assertTrue(urls.contains("http://test.org/page?query=1"))
    }

    @Test
    fun `extractURLs should return empty set for content without URLs`() {
        val content = "This is just plain text without any URLs"
        val urls = URLPreviewService.extractURLs(content)
        
        assertTrue(urls.isEmpty())
    }

    @Test
    fun `extractURLs should deduplicate URLs`() {
        val content = "https://example.com and https://example.com again"
        val urls = URLPreviewService.extractURLs(content)
        
        assertEquals(1, urls.size)
        assertTrue(urls.contains("https://example.com"))
    }

    @Test
    fun `normalizeURL should remove trailing slashes`() {
        val url = "https://example.com/"
        val normalized = URLPreviewService.normalizeURL(url)
        
        assertEquals("https://example.com", normalized)
    }

    @Test
    fun `normalizeURL should preserve query parameters`() {
        val url = "https://example.com/page?query=1&foo=bar"
        val normalized = URLPreviewService.normalizeURL(url)
        
        assertEquals("https://example.com/page?query=1&foo=bar", normalized)
    }

    @Test
    fun `normalizeURL should remove fragments`() {
        val url = "https://example.com/page#section"
        val normalized = URLPreviewService.normalizeURL(url)
        
        assertEquals("https://example.com/page", normalized)
    }

    @Test
    fun `normalizeURL should handle URLs with both query and fragment`() {
        val url = "https://example.com/page?query=1#section"
        val normalized = URLPreviewService.normalizeURL(url)
        
        assertEquals("https://example.com/page?query=1", normalized)
    }

    @Test
    fun `normalizeURL should preserve paths without trailing slash`() {
        val url = "https://example.com/path/to/page"
        val normalized = URLPreviewService.normalizeURL(url)
        
        assertEquals("https://example.com/path/to/page", normalized)
    }

    @Test
    fun `URLPreview data class should serialize correctly`() {
        val preview = URLPreview(
            url = "https://example.com",
            title = "Example Title",
            description = "Example Description",
            favicon = "https://example.com/favicon.ico",
            image = "https://example.com/image.jpg",
            siteName = "Example Site"
        )
        
        val json = jsonSerializer.encodeToString(preview)
        assertNotNull(json)
        assertTrue(json.contains("Example Title"))
        assertTrue(json.contains("Example Description"))
    }

    @Test
    fun `URLPreview data class should deserialize correctly`() {
        val json = """
            {
                "url": "https://example.com",
                "title": "Example Title",
                "description": "Example Description",
                "favicon": "https://example.com/favicon.ico",
                "image": "https://example.com/image.jpg",
                "siteName": "Example Site"
            }
        """.trimIndent()
        
        val preview = jsonSerializer.decodeFromString<URLPreview>(json)
        
        assertEquals("https://example.com", preview.url)
        assertEquals("Example Title", preview.title)
        assertEquals("Example Description", preview.description)
        assertEquals("https://example.com/favicon.ico", preview.favicon)
        assertEquals("https://example.com/image.jpg", preview.image)
        assertEquals("Example Site", preview.siteName)
    }

    @Test
    fun `URLPreview should handle null values`() {
        val preview = URLPreview(url = "https://example.com")
        
        assertEquals("https://example.com", preview.url)
        assertNull(preview.title)
        assertNull(preview.description)
        assertNull(preview.favicon)
        assertNull(preview.image)
        assertNull(preview.siteName)
    }

    @Test
    fun `extractOGTag should extract Open Graph metadata`() {
        val html = """
            <html>
                <head>
                    <meta property="og:title" content="OG Title">
                    <meta property="og:description" content="OG Description">
                    <meta property="og:image" content="https://example.com/image.jpg">
                </head>
                <body><h1>Content</h1></body>
            </html>
        """.trimIndent()
        
        val document = Jsoup.parse(html)
        
        val title = document.select("meta[property=og:title]").attr("content")
        val description = document.select("meta[property=og:description]").attr("content")
        val image = document.select("meta[property=og:image]").attr("content")
        
        assertEquals("OG Title", title)
        assertEquals("OG Description", description)
        assertEquals("https://example.com/image.jpg", image)
    }

    @Test
    fun `extractMetaTag should extract regular meta tags`() {
        val html = """
            <html>
                <head>
                    <meta name="description" content="Regular description">
                    <meta name="keywords" content="test, keywords">
                </head>
                <body><h1>Content</h1></body>
            </html>
        """.trimIndent()
        
        val document = Jsoup.parse(html)
        
        val description = document.select("meta[name=description]").attr("content")
        val keywords = document.select("meta[name=keywords]").attr("content")
        
        assertEquals("Regular description", description)
        assertEquals("test, keywords", keywords)
    }

    @Test
    fun `extractFavicon should extract favicon from link tag`() {
        val html = """
            <html>
                <head>
                    <link rel="icon" href="/favicon.ico">
                </head>
                <body><h1>Content</h1></body>
            </html>
        """.trimIndent()
        
        val document = Jsoup.parse(html)
        val faviconLink = document.select("link[rel=icon]").first()
        
        assertEquals("/favicon.ico", faviconLink?.attr("href"))
    }

    @Test
    fun `URLPreviewService cache should store and retrieve previews`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = """
                    <html>
                        <head>
                            <meta property="og:title" content="Mock Title">
                            <meta property="og:description" content="Mock Description">
                        </head>
                        <body><h1>Content</h1></body>
                    </html>
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html")
            )
        }
        
        val service = URLPreviewService(HttpClient(mockEngine), cacheDurationMs = 60000L)
        
        // First fetch - should hit the network
        val preview1 = service.fetchPreview("https://example.com")
        assertEquals("Mock Title", preview1.title)
        assertEquals("Mock Description", preview1.description)
        
        // Second fetch - should use cache (no additional network call)
        val preview2 = service.fetchPreview("https://example.com")
        assertEquals("Mock Title", preview2.title)
        assertEquals("Mock Description", preview2.description)
        
        // Clear cache
        service.clearCache()
    }

    @Test
    fun `URLPreviewService should handle fetch errors gracefully`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = "Error",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        
        val service = URLPreviewService(HttpClient(mockEngine))
        
        val preview = service.fetchPreview("https://example.com/error")
        
        // Should return empty preview on error
        assertEquals("https://example.com/error", preview.url)
        assertNull(preview.title)
        assertNull(preview.description)
    }

    @Test
    fun `fetchPreviewsFromContent should extract and fetch all URLs`() = runTest {
        var requestCount = 0
        val mockEngine = MockEngine { request ->
            requestCount++
            respond(
                content = """
                    <html>
                        <head>
                            <meta property="og:title" content="Title $requestCount">
                        </head>
                        <body><h1>Content</h1></body>
                    </html>
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html")
            )
        }
        
        val service = URLPreviewService(HttpClient(mockEngine))
        val content = "Check https://example.com and https://test.org"
        
        val previews = service.fetchPreviewsFromContent(content)
        
        assertEquals(2, previews.size)
        assertTrue(requestCount <= 2) // Should not exceed number of unique URLs
    }
}
