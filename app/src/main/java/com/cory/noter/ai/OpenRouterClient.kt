package com.cory.noter.ai

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface OpenRouterResult {
    data class Success(val responseText: String) : OpenRouterResult
    data class NetworkFailure(val reason: String) : OpenRouterResult
    data class RateLimited(val reason: String) : OpenRouterResult
    data class RemoteFailure(val code: Int, val reason: String) : OpenRouterResult
    data class InvalidResponse(val reason: String) : OpenRouterResult
}

interface OpenRouterGateway {
    suspend fun createChatCompletion(
        apiKey: String,
        modelId: String,
        prompt: String,
    ): OpenRouterResult
}

class OpenRouterClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : OpenRouterGateway {
    override suspend fun createChatCompletion(
        apiKey: String,
        modelId: String,
        prompt: String,
    ): OpenRouterResult = withContext(Dispatchers.IO) {
        val requestBody = json.encodeToString(
            ChatCompletionRequest(
                model = modelId,
                messages = listOf(
                    ChatMessage(
                        role = "user",
                        content = prompt,
                    ),
                ),
            ),
        )

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val reason = extractErrorMessage(responseText)
                    return@withContext when (response.code) {
                        429 -> OpenRouterResult.RateLimited(reason)
                        else -> OpenRouterResult.RemoteFailure(
                            code = response.code,
                            reason = reason,
                        )
                    }
                }

                val content = extractAssistantContent(responseText)
                    ?: return@withContext OpenRouterResult.InvalidResponse(
                        "OpenRouter response did not contain assistant content.",
                    )

                OpenRouterResult.Success(content)
            }
        } catch (error: IOException) {
            OpenRouterResult.NetworkFailure(error.message ?: "OpenRouter request failed.")
        }
    }

    private fun extractAssistantContent(responseText: String): String? = runCatching {
        json.parseToJsonElement(responseText).jsonObject["choices"]
            ?.jsonObjectSafeListFirst("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.content
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun extractErrorMessage(responseText: String): String = runCatching {
        json.parseToJsonElement(responseText).jsonObject["error"]
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.content
            ?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: "OpenRouter request failed."

    private fun kotlinx.serialization.json.JsonElement.jsonObjectSafeListFirst(
        key: String,
    ): kotlinx.serialization.json.JsonElement? {
        val choices = this as? kotlinx.serialization.json.JsonArray ?: return null
        val firstChoice = choices.firstOrNull()?.jsonObject ?: return null
        return firstChoice[key]
    }

    @Serializable
    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<ChatMessage>,
    )

    @Serializable
    private data class ChatMessage(
        val role: String,
        val content: String,
    )

    private companion object {
        const val DEFAULT_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
