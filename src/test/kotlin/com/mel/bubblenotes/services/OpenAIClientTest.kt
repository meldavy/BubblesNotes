package com.mel.bubblenotes.services

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenAIClientTest {

    @Test
    fun `HttpClient with MockEngine should return mock response`() = runTest {
        // Test that MockEngine works correctly with Ktor 3.x API
        
        val mockResponseContent = """
            {
                "choices": [
                    {
                        "message": {
                            "content": "{\"title\": \"Test Title\", \"summary\": \"Test summary\", \"tags\": [\"tag1\"]}"
                        }
                    }
                ]
            }
        """.trimIndent()
        
        // Create a mock HttpClient that intercepts the request
        // Using the Ktor 3.x MockEngine API
        val mockEngine = MockEngine { request ->
            // Return a mock response using the respond function
            respond(
                content = ByteReadChannel(mockResponseContent),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        
        // Make a request with headers in the request block
        val response = client.get("http://localhost:8080/test") {
            header(HttpHeaders.Authorization, "Bearer test-key")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        
        client.close()
    }
}
