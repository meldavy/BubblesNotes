package com.mel.bubblenotes.services

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class URLPreviewServiceIntegrationTest {
    private val jsonSerializer =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private var service: URLPreviewService? = null

    @AfterTest
    fun teardown() {
        service = null
    }

    // ==================== Caching Behavior Tests ====================

    @Test
    fun `cache hit should return cached preview without network call`() =
        runTest {
            var requestCount = 0
            val mockEngine =
                MockEngine { request ->
                    requestCount++
                    respond(
                        content =
                            """
                            <html>
                                <head>
                                    <meta property="og:title" content="Cached Title">
                                </head>
                                <body><h1>Content</h1></body>
                            </html>
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }

            service = URLPreviewService(HttpClient(mockEngine), cacheDurationMs = 60000L)

            // First fetch - should hit the network
            val preview1 = service!!.fetchPreview("https://example.com/cache-test")
            assertEquals(1, requestCount)
            assertEquals("Cached Title", preview1.title)

            // Second fetch - should use cache (no additional network call)
            val preview2 = service!!.fetchPreview("https://example.com/cache-test")
            assertEquals(1, requestCount) // Still 1, cache was used
            assertEquals("Cached Title", preview2.title)
        }

    @Test
    fun `cache miss should fetch from network`() =
        runTest {
            var requestCount = 0
            val mockEngine =
                MockEngine { request ->
                    requestCount++
                    respond(
                        content =
                            """
                            <html>
                                <head>
                                    <meta property="og:title" content="Fresh Title">
                                </head>
                                <body><h1>Content</h1></body>
                            </html>
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }

            service = URLPreviewService(HttpClient(mockEngine), cacheDurationMs = 60000L)

            // Fetch different URLs - each should hit the network
            service!!.fetchPreview("https://example.com/page1")
            assertEquals(1, requestCount)

            service!!.fetchPreview("https://example.com/page2")
            assertEquals(2, requestCount)

            service!!.fetchPreview("https://example.com/page3")
            assertEquals(3, requestCount)
        }

    @Test
    fun `cache expiration should trigger network fetch`() =
        runTest {
            var requestCount = 0
            val mockEngine =
                MockEngine { request ->
                    requestCount++
                    respond(
                        content =
                            """
                            <html>
                                <head>
                                    <meta property="og:title" content="Title v$requestCount">
                                </head>
                                <body><h1>Content</h1></body>
                            </html>
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }

            // Helper class for manual time control
            class TestURLPreviewService(client: HttpClient, duration: Long) : URLPreviewService(client, duration) {
                var manualTime = 0L

                override fun getCurrentTime(): Long = manualTime

                fun advanceTime(ms: Long) {
                    manualTime += ms
                }
            }

            val testService = TestURLPreviewService(HttpClient(mockEngine), 100L)
            service = testService

            // First fetch
            val preview1 = testService.fetchPreview("https://example.com/expire-test")
            assertEquals(1, requestCount)
            assertEquals("Title v1", preview1.title)

            // Second fetch immediately - should use cache
            val preview2 = testService.fetchPreview("https://example.com/expire-test")
            assertEquals(1, requestCount) // Still cached
            assertEquals("Title v1", preview2.title)

            // Wait for cache to expire
            testService.advanceTime(150L)

            // Third fetch after expiration - should hit network again
            val preview3 = testService.fetchPreview("https://example.com/expire-test")
            assertEquals(2, requestCount) // New request made
            assertEquals("Title v2", preview3.title)
        }

    // ==================== Timeout Handling Tests ====================

    @Test
    fun `timeout should return empty preview after 5 seconds`() =
        runTest {
            // Create a mock engine that delays response beyond timeout
            val mockEngine =
                MockEngine { request ->
                    // Simulate slow response (will be cancelled by timeout)
                    delay(10000L)
                    respond(
                        content = "<html><title>Slow</title></html>",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }

            service = URLPreviewService(HttpClient(mockEngine), cacheDurationMs = 60000L)

            // This should timeout and return empty preview
            val preview = service!!.fetchPreview("https://example.com/slow")

            // Should return empty preview on timeout
            assertEquals("https://example.com/slow", preview.url)
            assertNull(preview.title)
            assertNull(preview.description)
        }

    @Test
    fun `normal response should complete within timeout`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content =
                            """
                            <html>
                                <head>
                                    <meta property="og:title" content="Fast Title">
                                    <meta property="og:description" content="Fast Description">
                                </head>
                                <body><h1>Content</h1></body>
                            </html>
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }

            service = URLPreviewService(HttpClient(mockEngine), cacheDurationMs = 60000L)

            // Should complete quickly
            val preview = service!!.fetchPreview("https://example.com/fast")

            assertEquals("Fast Title", preview.title)
            assertEquals("Fast Description", preview.description)
        }

    // ==================== URL Extraction Tests ====================

    @Test
    fun `extractURLs should extract plain URLs from Markdown content`() {
        val content = "Check out https://example.com and http://test.org/page for more info"
        val urls = URLPreviewService.extractURLs(content)

        assertEquals(2, urls.size)
        assertTrue(urls.contains("https://example.com"))
        assertTrue(urls.contains("http://test.org/page"))
    }

    @Test
    fun `extractURLs should extract URLs from markdown links`() {
        val content = "Visit [Example Site](https://example.com) or [Test Page](http://test.org/page)"
        val urls = URLPreviewService.extractURLs(content)

        assertEquals(2, urls.size)
        assertTrue(urls.contains("https://example.com"))
        assertTrue(urls.contains("http://test.org/page"))
    }

    @Test
    fun `extractURLs should handle mixed plain URLs and markdown links`() {
        val content =
            """
            Here's a plain URL: https://example.com
            And a markdown link: [Google](https://google.com)
            Another URL: http://test.org/page?query=1
            And another link: [GitHub](https://github.com)
            """.trimIndent()
        val urls = URLPreviewService.extractURLs(content)

        assertEquals(4, urls.size)
        assertTrue(urls.contains("https://example.com"))
        assertTrue(urls.contains("https://google.com"))
        assertTrue(urls.contains("http://test.org/page?query=1"))
        assertTrue(urls.contains("https://github.com"))
    }

    @Test
    fun `extractURLs should return empty set for content without URLs`() {
        val content = "This is just plain text without any URLs or links"
        val urls = URLPreviewService.extractURLs(content)

        assertTrue(urls.isEmpty())
    }

    @Test
    fun `extractURLs should deduplicate URLs across different formats`() {
        val content = "Visit https://example.com or [Example](https://example.com) for info"
        val urls = URLPreviewService.extractURLs(content)

        assertEquals(1, urls.size)
        assertTrue(urls.contains("https://example.com"))
    }

    // ==================== Preview Fetching Tests ====================

    @Test
    fun `fetchPreview should extract Open Graph metadata`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content =
                            """
                            <html>
                                <head>
                                    <meta property="og:title" content="OG Title">
                                    <meta property="og:description" content="OG Description">
                                    <meta property="og:image" content="https://example.com/image.jpg">
                                    <meta property="og:site_name" content="OG Site">
                                    <link rel="icon" href="/favicon.ico">
                                </head>
                                <body><h1>Content</h1></body>
                            </html>
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }

            service = URLPreviewService(HttpClient(mockEngine), cacheDurationMs = 60000L)

            val preview = service!!.fetchPreview("https://example.com/og-test")

            assertEquals("OG Title", preview.title)
            assertEquals("OG Description", preview.description)
            assertEquals("https://example.com/image.jpg", preview.image)
            assertEquals("OG Site", preview.siteName)
            assertEquals("https://example.com/favicon.ico", preview.favicon)
        }

    @Test
    fun `fetchPreview should fallback to regular meta tags when OG tags missing`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content =
                            """
                            <html>
                                <head>
                                    <title>Page Title</title>
                                    <meta name="description" content="Regular description">
                                </head>
                                <body><h1>Content</h1></body>
                            </html>
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }

            service = URLPreviewService(HttpClient(mockEngine), cacheDurationMs = 60000L)

            val preview = service!!.fetchPreview("https://example.com/fallback-test")

            assertEquals("Page Title", preview.title)
            assertEquals("Regular description", preview.description)
        }

    @Test
    fun `fetchPreview should handle missing metadata gracefully`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = "<html><body><h1>No metadata here</h1></body></html>",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }

            service = URLPreviewService(HttpClient(mockEngine), cacheDurationMs = 60000L)

            val preview = service!!.fetchPreview("https://example.com/no-meta")

            assertEquals("https://example.com/no-meta", preview.url)
            // Title might be empty string from Jsoup, but should not crash
            assertNotNull(preview) // Jsoup returns empty string if no title
        }

    // ==================== Error Handling Tests ====================

    @Test
    fun `fetchPreview should handle HTTP error responses`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = "Not Found",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                    )
                }

            service = URLPreviewService(HttpClient(mockEngine), cacheDurationMs = 60000L)

            val preview = service!!.fetchPreview("https://example.com/not-found")

            assertEquals("https://example.com/not-found", preview.url)
            assertNull(preview.title)
            assertNull(preview.description)
        }

    @Test
    fun `fetchPreview should handle server errors gracefully`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = "Internal Server Error",
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                    )
                }

            service = URLPreviewService(HttpClient(mockEngine), cacheDurationMs = 60000L)

            val preview = service!!.fetchPreview("https://example.com/error")

            assertEquals("https://example.com/error", preview.url)
            assertNull(preview.title)
            assertNull(preview.description)
        }

    @Test
    fun `fetchPreview should handle empty response body`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = "",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }

            service = URLPreviewService(HttpClient(mockEngine), cacheDurationMs = 60000L)

            val preview = service!!.fetchPreview("https://example.com/empty")

            assertEquals("https://example.com/empty", preview.url)
            // Should not crash, title might be empty
            assertNotNull(preview)
        }

    @Test
    fun `fetchPreview should handle malformed HTML gracefully`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = "<html><head><meta property=\"og:title\" content=\"Broken",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }

            service = URLPreviewService(HttpClient(mockEngine), cacheDurationMs = 60000L)

            // Should not throw exception
            val preview = service!!.fetchPreview("https://example.com/malformed")

            assertEquals("https://example.com/malformed", preview.url)
            assertNotNull(preview)
        }

    // ==================== URL Normalization Tests ====================

    @Test
    fun `normalizeURL should remove trailing slashes for consistent caching`() {
        val url1 = "https://example.com/"
        val url2 = "https://example.com"

        val normalized1 = URLPreviewService.normalizeURL(url1)
        val normalized2 = URLPreviewService.normalizeURL(url2)

        assertEquals(normalized1, normalized2)
        assertEquals("https://example.com", normalized1)
    }

    @Test
    fun `normalizeURL should preserve query parameters`() {
        val url = "https://example.com/page?query=1&foo=bar"
        val normalized = URLPreviewService.normalizeURL(url)

        assertEquals("https://example.com/page?query=1&foo=bar", normalized)
    }

    @Test
    fun `normalizeURL should remove fragments for consistent caching`() {
        val url1 = "https://example.com/page#section"
        val url2 = "https://example.com/page"

        val normalized1 = URLPreviewService.normalizeURL(url1)
        val normalized2 = URLPreviewService.normalizeURL(url2)

        assertEquals(normalized1, normalized2)
        assertEquals("https://example.com/page", normalized1)
    }

    @Test
    fun `normalizeURL should handle URLs with both query and fragment`() {
        val url = "https://example.com/page?query=1#section"
        val normalized = URLPreviewService.normalizeURL(url)

        assertEquals("https://example.com/page?query=1", normalized)
    }

    @Test
    fun `normalized URLs should use same cache entry`() =
        runTest {
            var requestCount = 0
            val mockEngine =
                MockEngine { request ->
                    requestCount++
                    respond(
                        content = "<html><head><meta property=\"og:title\" content=\"Cached\"></head></html>",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }

            service = URLPreviewService(HttpClient(mockEngine), cacheDurationMs = 60000L)

            // Fetch with trailing slash
            service!!.fetchPreview("https://example.com/test/")
            assertEquals(1, requestCount)

            // Fetch without trailing slash - should use cache
            service!!.fetchPreview("https://example.com/test")
            assertEquals(1, requestCount) // Cache hit

            // Fetch with fragment - should use cache
            service!!.fetchPreview("https://example.com/test#section")
            assertEquals(1, requestCount) // Cache hit
        }

    @Test
    fun `truncateIfNeeded should truncate long titles`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    // Create a very long title (200 characters)
                    val longTitle = "A".repeat(200)
                    respond(
                        content = "<html><head><meta property=\"og:title\" content=\"$longTitle\"></head></html>",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }

            service = URLPreviewService(HttpClient(mockEngine), cacheDurationMs = 60000L)

            val preview = service!!.fetchPreview("https://example.com/long-title")

            // Title should be truncated to 150 characters with ellipsis
            assertEquals(150, preview.title?.length)
            assertTrue(preview.title?.endsWith("...") == true)
        }

    @Test
    fun `truncateIfNeeded should truncate long descriptions`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    // Create a very long description (250 characters)
                    val longDesc = "B".repeat(250)
                    respond(
                        content = "<html><head><meta property=\"og:description\" content=\"$longDesc\"></head></html>",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }

            service = URLPreviewService(HttpClient(mockEngine), cacheDurationMs = 60000L)

            val preview = service!!.fetchPreview("https://example.com/long-desc")

            // Description should be truncated to 200 characters with ellipsis
            assertEquals(200, preview.description?.length)
            assertTrue(preview.description?.endsWith("...") == true)
        }
}
