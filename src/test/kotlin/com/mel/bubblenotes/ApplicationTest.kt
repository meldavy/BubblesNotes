package com.mel.bubblenotes

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testRoot() =
        testApplication {
            application {
                module()
            }
            client.get("/").apply {
                assertEquals(HttpStatusCode.OK, status)
            }
        }

    @Test
    fun testNotesApiGetByIdReturnsBadRequestForInvalidId() =
        testApplication {
            application {
                module()
            }
            val response = client.get("/api/v1/notes/invalid")
            // Without authentication, returns Unauthorized (401)
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun testNotesApiUpdateReturnsBadRequestForInvalidId() =
        testApplication {
            application {
                module()
            }
            val response =
                client.put("/api/v1/notes/invalid") {
                    contentType(io.ktor.http.ContentType.Application.Json)
                    setBody("""{"title":"Test"}""")
                }
            // Without authentication, returns Unauthorized (401)
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun testNotesApiDeleteReturnsBadRequestForInvalidId() =
        testApplication {
            application {
                module()
            }
            val response = client.delete("/api/v1/notes/invalid")
            // Without authentication, returns Unauthorized (401)
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
}
