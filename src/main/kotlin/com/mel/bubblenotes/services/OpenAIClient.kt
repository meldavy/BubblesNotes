package com.mel.bubblenotes.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.setBody
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * OpenAI API client for AI-powered note enhancement.
 *
 * Supports:
 * - AI-generated summaries (only for notes exceeding character threshold)
 * - AI-generated titles
 * - AI-suggested tags (with context of user's existing tags)
 *
 * Configuration via application.yaml:
 * - ai.openai.api-key: OpenAI API key (required)
 * - ai.openai.api-url: Custom API URL (default: https://api.openai.com/v1)
 * - ai.openai.model-id: Model to use (default: gpt-4o-mini)
 * - ai.openai.summary-threshold: Minimum character count for summarization (default: 500)
 * - ai.openai.request-timeout: Request timeout in ms (default: 120000 = 2 minutes for local LLMs)
 */
class OpenAIClient(
    private val apiKey: String,
    private val apiUrl: String = "https://api.openai.com/v1",
    private val modelId: String = "gpt-4o-mini",
    private val summaryThreshold: Int = 500,
    private val requestTimeout: Long = 120000
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(OpenAIClient::class.java)
    }
    
    init {
        logger.info("OpenAIClient initialized - API URL: $apiUrl, Model: $modelId, Summary Threshold: ${summaryThreshold} chars, Timeout: ${requestTimeout}ms")
        logger.debug("OpenAI API key configured: ${if (apiKey.isNotBlank()) "YES (${apiKey.length} chars)" else "NO"}")
    }
    private val client = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = requestTimeout
            connectTimeoutMillis = 30000
            socketTimeoutMillis = requestTimeout
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        defaultRequest {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }
    }

    /**
     * AI task result containing generated title, summary, and suggested tags.
     */
    @Serializable
    data class AITaskResult(
        val aiTitle: String? = null,
        val aiSummary: String? = null,
        val aiTags: List<String> = emptyList()
    )

    /**
     * OpenAI Chat Completions request body.
     */
    @Serializable
    data class ChatCompletionRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double,
        val max_tokens: Int
    )

    /**
     * Chat message for OpenAI API.
     */
    @Serializable
    data class Message(
        val role: String,
        val content: String
    )

    /**
     * Check if note content meets the threshold for AI summarization.
     * @param content The note content to check
     * @return true if content length exceeds the summary threshold
     */
    fun shouldGenerateSummary(content: String): Boolean {
        return content.length >= summaryThreshold
    }

    /**
     * Generate all AI enhancements (title, summary, tags) in a single call.
     *
     * @param content The note content
     * @param existingTags The user's existing tags
     * @return AITaskResult with all generated data
     */
    suspend fun generateAllAIEnhancements(
        content: String,
        existingTags: List<String> = emptyList()
    ): AITaskResult {
        logger.info("generateAllAIEnhancements called - content length: ${content.length} chars, existingTags: ${existingTags.size}")
        logger.debug("generateAllAIEnhancements: content preview (first 200 chars):\n${content.take(200)}")
        
        if (apiKey.isBlank()) {
            logger.warn("generateAllAIEnhancements: API key is blank, returning empty result")
            return AITaskResult()
        }

        val shouldSummarize = shouldGenerateSummary(content)
        logger.info("generateAllAIEnhancements: shouldSummarize=$shouldSummarize (threshold: $summaryThreshold)")
        val existingTagsContext = if (existingTags.isNotEmpty()) {
            """
            The user already has these tags: ${existingTags.joinToString(", ")}
            - ONLY suggest NEW tags if they would be broadly applicable across many notes
            - Avoid overly specific or narrow tags that would only apply to this single note
            - PRIORITIZE reusing existing tags if they match the content
            - Limit new tag suggestions to 1-2 maximum, only if truly essential
            """.trimIndent()
        } else {
            "Suggest 2-3 broadly applicable tags for this content. Avoid overly specific tags."
        }

        val summaryInstruction = if (shouldSummarize) {
            "2. A brief summary (2-3 sentences, max 200 characters)"
        } else {
            "2. Skip summary (note is short, under ${summaryThreshold} characters)"
        }

        val prompt = """
            You are an AI assistant that generates metadata for notes. Respond ONLY with valid JSON, no reasoning or thinking process.

            Analyze the following note content and provide AI enhancements:

            1. A concise, descriptive title (max 100 characters)
            $summaryInstruction
            3. Relevant tags (${existingTagsContext})

            Note content:
            $content

            Respond in JSON format ONLY - no additional text:
            {
                "title": "generated title",
                "summary": "generated summary or null",
//                "tags": ["tag1", "tag2", "tag3"]
            }
        """.trimIndent()

        logger.debug("generateAllAIEnhancements: Sending request to OpenAI API")
        logger.trace("generateAllAIEnhancements: Full prompt:\n$prompt")
        
        val request = ChatCompletionRequest(
            model = modelId,
            messages = listOf(Message("user", prompt)),
            temperature = 0.7,
            max_tokens = 10000
        )
        
        val response = client.post("$apiUrl/chat/completions") {
            setBody(request)
        }

        logger.info("generateAllAIEnhancements: OpenAI API response status: ${response.status}")

        if (!response.status.isSuccess()) {
            val errorBody = response.body<String>()
            logger.error("generateAllAIEnhancements: API request failed - Status: ${response.status}, Body: $errorBody")
            throw OpenAIException("API request failed: ${response.status} - $errorBody")
        }

        val result = response.bodyAsText()
        
        // Log the full raw response for debugging
        logger.info("generateAllAIEnhancements: Raw API response (first 2000 chars):\n${result.take(2000)}")
        
        val rawContent = extractContentFromResponse(result, "generateAllAIEnhancements")
        logger.info("generateAllAIEnhancements: Extracted content from response (first 1000 chars):\n${rawContent?.take(1000)}")

        val parsedResult = parseAIResponse(rawContent, shouldSummarize)
        logger.info("generateAllAIEnhancements: Parsed result - title: ${parsedResult.aiTitle?.take(100)}, summary: ${parsedResult.aiSummary?.take(100)}, tags: ${parsedResult.aiTags.size}")
        return parsedResult
    }

    private fun parseAIResponse(rawContent: String?, shouldSummarize: Boolean): AITaskResult {
        logger.debug("parseAIResponse: shouldSummarize=$shouldSummarize, rawContent length: ${rawContent?.length ?: 0}")
        
        if (rawContent == null) {
            logger.warn("parseAIResponse: rawContent is null, returning empty result")
            return AITaskResult()
        }

        return try {
            // Clean up the response (remove markdown code blocks if present)
            val cleanedContent = rawContent
                .replace("```json", "")
                .replace("```", "")
                .trim()

            // Parse JSON manually since we don't have kotlinx.serialization for dynamic parsing
            val title = extractJsonValue(cleanedContent, "title")
            val summary = if (shouldSummarize) {
                extractJsonValue(cleanedContent, "summary")
            } else {
                null
            }
            val tags = extractTagsFromJson(cleanedContent)

            AITaskResult(
                aiTitle = title.takeIf { it?.isNotBlank() == true },
                aiSummary = summary?.takeIf { it.isNotBlank() },
                aiTags = tags
            )
        } catch (e: Exception) {
            logger.error("parseAIResponse: Failed to parse AI response: ${e.message}", e)
            throw OpenAIException("Failed to parse AI response: ${e.message}", e)
        }
    }

    private fun parseTagResponse(rawContent: String?): List<String> {
        logger.debug("parseTagResponse: rawContent length: ${rawContent?.length ?: 0}")
        
        if (rawContent == null) {
            logger.warn("parseTagResponse: rawContent is null, returning empty list")
            return emptyList()
        }

        return try {
            val cleanedContent = rawContent
                .replace("```json", "")
                .replace("```", "")
                .trim()

            // Extract tags from JSON array
            val tagsRegex = """\["([^"]+)"(,\s*"([^"]+)")*]""".toRegex()
            val matchResult = tagsRegex.find(cleanedContent)

            if (matchResult != null) {
                val tagsString = matchResult.value
                // Extract all quoted strings
                val tagRegex = """"([^"]+)" """.toRegex()
                tagRegex.findAll(tagsString)
                    .map { it.groupValues[1] }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(5)
                    .toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger.warn("parseTagResponse: Failed to parse tags: ${e.message}")
            emptyList()
        }
    }

    private fun extractContentFromResponse(responseBody: String, methodName: String): String? {
        return try {
            val json = Json { isLenient = true; ignoreUnknownKeys = true }
            val parsed = json.parseToJsonElement(responseBody)
            logger.debug("$methodName: Parsed JSON successfully, type: ${parsed::class.simpleName}")
            
            val choices = parsed.jsonObject["choices"]?.jsonArray
            logger.debug("$methodName: Found ${choices?.size ?: 0} choices")
            
            val firstChoice = choices?.firstOrNull()?.jsonObject
            val message = firstChoice?.get("message")?.jsonObject
            
            // First try to get content from the standard "content" field
            var content = message?.get("content")?.jsonPrimitive?.content
            logger.debug("$methodName: Extracted content from 'content' field, length: ${content?.length ?: 0}")
            
            // If content is empty, try the "reasoning" field (some models use this for chain-of-thought)
            if (content.isNullOrBlank()) {
                content = message?.get("reasoning")?.jsonPrimitive?.content
                logger.info("$methodName: Content was empty, extracted from 'reasoning' field, length: ${content?.length ?: 0}")
            }
            
            return content
        } catch (e: Exception) {
            logger.warn("$methodName: Failed to parse JSON response: ${e.message}, trying regex fallback")
            logger.debug("$methodName: Exception details:", e)
            // Fallback: try to extract content using regex
            val contentRegex = """\"content\"\s*:\s*"((?:[^"\\]|\\.)*)\"""".toRegex()
            val match = contentRegex.find(responseBody)
            if (match != null) {
                val extracted = match.groupValues[1].replace("\\n", "\n").replace("\\\"", "\"")
                logger.info("$methodName: Regex fallback extracted ${extracted.length} chars")
                return extracted
            }
            logger.error("$methodName: Both JSON parsing and regex fallback failed")
            null
        }
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val regex = """\"$key\"\s*:\s*"([^"]*)\"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractTagsFromJson(json: String): List<String> {
        logger.debug("extractTagsFromJson: Extracting tags from JSON")
        
        // Try to extract tags array using regex
        val tagsRegex = """\"tags\"\s*:\s*\[([^\]]*)\]""".toRegex()
        val match = tagsRegex.find(json)
        
        if (match != null) {
            val tagsArray = match.groupValues[1]
            // Extract all quoted strings from the array
            val tagRegex = """"([^"]+)"""".toRegex()
            val tags = tagRegex.findAll(tagsArray)
                .map { it.groupValues[1] }
                .filter { it.isNotBlank() }
                .distinct()
                .take(5)
                .toList()
            logger.info("extractTagsFromJson: Extracted ${tags.size} tags: $tags")
            return tags
        }
        
        logger.debug("extractTagsFromJson: No tags found in JSON")
        return emptyList()
    }

    /**
     * Close the HTTP client.
     */
    fun close() {
        client.close()
    }
}

/**
 * Exception thrown when OpenAI API requests fail.
 */
class OpenAIException(message: String, cause: Throwable? = null) : Exception(message, cause)
