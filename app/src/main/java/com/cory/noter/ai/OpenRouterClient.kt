package com.cory.noter.ai

import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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
                tools = listOf(alarmDraftTool()),
                toolChoice = ToolChoice(
                    type = "function",
                    function = ToolChoiceFunction(name = ALARM_DRAFT_TOOL_NAME),
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

        val arguments = extractAlarmDraftArguments(responseText)
        if (arguments == null) {
            debugLogger.warn(
                "response.invalid $responseSummary bodyPreview=${responseText.previewForLog()}",
            )
            return OpenRouterResult.InvalidResponse(
                "OpenRouter response did not contain a submit_alarm_draft tool call.",
            )
        }

        debugLogger.debug("response.success $responseSummary argumentChars=${arguments.length}")
        return OpenRouterResult.Success(arguments)
    }

    private fun extractAlarmDraftArguments(responseText: String): String? = runCatching {
        val choices = json.parseToJsonElement(responseText).jsonObject["choices"] as? JsonArray
            ?: return@runCatching null
        choices
            .mapNotNull { choice ->
                choice.jsonObject["message"]
                    ?.jsonObject
                    ?.get("tool_calls") as? JsonArray
            }
            .flatMap { it }
            .firstNotNullOfOrNull { toolCall ->
                val function = toolCall.jsonObject["function"]?.jsonObject ?: return@firstNotNullOfOrNull null
                val name = function["name"]?.jsonPrimitive?.content
                val arguments = function["arguments"]?.jsonPrimitive?.content
                arguments?.takeIf { name == ALARM_DRAFT_TOOL_NAME && it.isNotBlank() }
            }
    }.getOrNull()

    private fun extractErrorMessage(responseText: String): String = runCatching {
        json.parseToJsonElement(responseText).jsonObject["error"]
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.content
            ?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: "OpenRouter request failed."

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
        val tools: List<ChatTool>,
        @SerialName("tool_choice")
        val toolChoice: ToolChoice,
    )

    @Serializable
    private data class ChatMessage(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class ChatTool(
        val type: String,
        val function: ToolFunction,
    )

    @Serializable
    private data class ToolFunction(
        val name: String,
        val description: String,
        val parameters: JsonObject,
    )

    @Serializable
    private data class ToolChoice(
        val type: String,
        val function: ToolChoiceFunction,
    )

    @Serializable
    private data class ToolChoiceFunction(
        val name: String,
    )

    private fun alarmDraftTool(): ChatTool = ChatTool(
        type = "function",
        function = ToolFunction(
            name = ALARM_DRAFT_TOOL_NAME,
            description = "Submit one validated alarm draft for the user's Android alarm request.",
            parameters = alarmDraftParameters(),
        ),
    )

    private fun alarmDraftParameters(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put("description", "Short alarm title, such as Take medicine.")
            }
            putJsonObject("hour") {
                put("type", "integer")
                put("minimum", 0)
                put("maximum", 23)
                put("description", "Local 24-hour clock hour.")
            }
            putJsonObject("minute") {
                put("type", "integer")
                put("minimum", 0)
                put("maximum", 59)
                put("description", "Local clock minute.")
            }
            putJsonObject("repeatRule") {
                put("type", "object")
                put("additionalProperties", false)
                putJsonObject("properties") {
                    putJsonObject("type") {
                        put("type", "string")
                        putJsonArray("enum") {
                            addString("once")
                            addString("daily")
                            addString("weekdays")
                            addString("custom_weekdays")
                            addString("weekly_interval")
                        }
                    }
                    putJsonObject("daysOfWeek") {
                        put("type", "array")
                        put(
                            "description",
                            "ISO weekdays for custom_weekdays and weekly_interval. Monday is 1 and Sunday is 7.",
                        )
                        putJsonObject("items") {
                            put("type", "integer")
                            put("minimum", 1)
                            put("maximum", 7)
                        }
                    }
                    putJsonObject("startDate") {
                        putJsonArray("type") {
                            addString("string")
                            addString("null")
                        }
                        put("description", "ISO local date yyyy-MM-dd required for weekly_interval; use null otherwise.")
                    }
                    putJsonObject("endDate") {
                        putJsonArray("type") {
                            addString("string")
                            addString("null")
                        }
                        put(
                            "description",
                            "ISO local date yyyy-MM-dd for weekly_interval when the user gives an end date; use null so the app defaults to one year after startDate when omitted.",
                        )
                    }
                    putJsonObject("intervalWeeks") {
                        putJsonArray("type") {
                            addString("integer")
                            addString("null")
                        }
                        put("minimum", 1)
                        put("description", "Positive week interval required for weekly_interval; use null otherwise.")
                    }
                }
                putJsonArray("required") {
                    addString("type")
                    addString("daysOfWeek")
                }
            }
            putJsonObject("date") {
                putJsonArray("type") {
                    addString("string")
                    addString("null")
                }
                put("description", "ISO local date yyyy-MM-dd for once alarms. Use null for repeating alarms.")
            }
            putJsonObject("confidence") {
                put("type", "number")
                put("minimum", 0.0)
                put("maximum", 1.0)
            }
            putJsonObject("needsClarification") {
                put("type", "boolean")
            }
            putJsonObject("clarificationReason") {
                put("type", "string")
                put("description", "Non-empty only when needsClarification is true.")
            }
        }
        putJsonArray("required") {
            addString("title")
            addString("hour")
            addString("minute")
            addString("repeatRule")
            addString("date")
            addString("confidence")
            addString("needsClarification")
            addString("clarificationReason")
        }
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addString(value: String) {
        add(kotlinx.serialization.json.JsonPrimitive(value))
    }

    private companion object {
        const val DEFAULT_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        const val ALARM_DRAFT_TOOL_NAME = "submit_alarm_draft"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
