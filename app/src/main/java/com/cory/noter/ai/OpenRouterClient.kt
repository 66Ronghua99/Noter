package com.cory.noter.ai

import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

internal fun defaultOpenRouterCallFactory(): Call.Factory =
    OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

interface OpenRouterDebugLogger {
    fun debug(message: String)
    fun warn(message: String, error: Throwable? = null)
}

object NoOpOpenRouterDebugLogger : OpenRouterDebugLogger {
    override fun debug(message: String) = Unit
    override fun warn(message: String, error: Throwable?) = Unit
}

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
    private val callFactory: Call.Factory = defaultOpenRouterCallFactory(),
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val debugLogger: OpenRouterDebugLogger = NoOpOpenRouterDebugLogger,
) : OpenRouterGateway {
    override suspend fun createChatCompletion(
        apiKey: String,
        modelId: String,
        prompt: String,
    ): OpenRouterResult = suspendCancellableCoroutine { continuation ->
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

        debugLogger.debug(
            "request.start endpoint=$endpoint model=$modelId " +
                "promptChars=${prompt.length} apiKeyChars=${apiKey.length}",
        )
        try {
            val call = callFactory.newCall(request)
            continuation.invokeOnCancellation {
                debugLogger.debug("request.cancelled model=$modelId")
                call.cancel()
            }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!continuation.isActive) {
                            return
                        }
                        debugLogger.warn(
                            "request.networkFailure type=${e.javaClass.simpleName} " +
                                "message=${e.message.orEmpty()}",
                            e,
                        )
                        continuation.resume(
                            OpenRouterResult.NetworkFailure(
                                e.message ?: "OpenRouter request failed.",
                            ),
                        )
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = response.use { toResult(it, modelId) }
                        if (!continuation.isActive) {
                            return
                        }
                        continuation.resume(result)
                    }
                }
            )
        } catch (error: IOException) {
            debugLogger.warn(
                "request.createFailure type=${error.javaClass.simpleName} " +
                    "message=${error.message.orEmpty()}",
                error,
            )
            continuation.resume(
                OpenRouterResult.NetworkFailure(
                    error.message ?: "OpenRouter request failed.",
                ),
            )
        }
    }

    private fun toResult(response: Response, modelId: String): OpenRouterResult {
        val responseText = response.body?.string().orEmpty()
        val responseSummary = response.summaryForLogs(modelId)
        if (!response.isSuccessful) {
            val reason = extractErrorMessage(responseText)
            debugLogger.warn(
                "response.remoteFailure $responseSummary reason=${reason.compactForLog()} " +
                    "bodyPreview=${responseText.previewForLog()}",
            )
            return when (response.code) {
                429 -> OpenRouterResult.RateLimited(reason)
                else -> OpenRouterResult.RemoteFailure(
                    code = response.code,
                    reason = reason,
                )
            }
        }

        val content = extractAssistantContent(responseText)
        if (content == null) {
            debugLogger.warn(
                "response.invalid $responseSummary bodyPreview=${responseText.previewForLog()}",
            )
            return OpenRouterResult.InvalidResponse(
                "OpenRouter response did not contain assistant content.",
            )
        }

        debugLogger.debug("response.success $responseSummary contentChars=${content.length}")
        return OpenRouterResult.Success(content)
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

    private fun Response.summaryForLogs(modelId: String): String =
        "code=$code message=${message.compactForLog()} model=$modelId " +
            "requestId=${header("x-request-id").orEmpty().ifBlank { "missing" }}"

    private fun String.compactForLog(): String = replace(Regex("\\s+"), " ").trim()

    private fun String.previewForLog(maxChars: Int = 500): String =
        compactForLog().let { compact ->
            if (compact.length <= maxChars) compact else compact.take(maxChars) + "...<truncated>"
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
