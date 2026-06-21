package com.cory.noter.ai

import com.cory.noter.agent.AgentLlmGateway
import com.cory.noter.agent.AgentLlmRequest
import com.cory.noter.agent.AgentLlmResult
import com.cory.noter.agent.AgentMessage
import com.cory.noter.agent.AgentMessageRole
import com.cory.noter.agent.AgentToolCall
import com.cory.noter.agent.AgentToolChoice
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class OpenRouterAgentClient(
    private val callFactory: Call.Factory = defaultOpenRouterCallFactory(),
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val debugLogger: OpenRouterDebugLogger = NoOpOpenRouterDebugLogger,
) : AgentLlmGateway {
    override suspend fun complete(request: AgentLlmRequest): AgentLlmResult =
        suspendCancellableCoroutine { continuation ->
            val requestBody = json.encodeToString(ChatCompletionRequest.serializer(), request.toChatRequest())
            val httpRequest = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer ${request.apiKey}")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            debugLogger.debug(
                "request.start endpoint=$endpoint model=${request.modelId} " +
                    "messages=${request.messages.size} tools=${request.tools.size}",
            )

            try {
                val call = callFactory.newCall(httpRequest)
                continuation.invokeOnCancellation {
                    debugLogger.debug("request.cancelled model=${request.modelId}")
                    call.cancel()
                }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            if (continuation.isActive) {
                                debugLogger.warn(
                                    "request.networkFailure type=${e.javaClass.simpleName} " +
                                        "message=${e.message.orEmpty()}",
                                    e,
                                )
                                continuation.resume(
                                    AgentLlmResult.NetworkFailure(
                                        e.message ?: "OpenRouter request failed.",
                                    ),
                                )
                            }
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val result = response.use { toResult(it) }
                            if (continuation.isActive) {
                                continuation.resume(result)
                            }
                        }
                    },
                )
            } catch (error: IOException) {
                debugLogger.warn(
                    "request.createFailure type=${error.javaClass.simpleName} " +
                        "message=${error.message.orEmpty()}",
                    error,
                )
                continuation.resume(
                    AgentLlmResult.NetworkFailure(
                        error.message ?: "OpenRouter request failed.",
                    ),
                )
            }
        }

    private fun AgentLlmRequest.toChatRequest(): ChatCompletionRequest = ChatCompletionRequest(
        model = modelId,
        messages = messages.map { it.toChatMessage() },
        tools = tools.map { ChatTool("function", ToolFunction(it.name, it.description, it.parameters)) },
        toolChoice = when (val choice = toolChoice) {
            AgentToolChoice.Auto -> null
            is AgentToolChoice.Required -> ToolChoice("function", ToolChoiceFunction(choice.toolName))
        },
    )

    private fun AgentMessage.toChatMessage(): ChatMessage = ChatMessage(
        role = role.toOpenRouterRole(),
        content = content,
        toolCallId = toolCallId,
        name = toolName,
        toolCalls = toolCalls.takeIf { it.isNotEmpty() }?.map {
            ChatToolCall(it.id, "function", ToolCallFunction(it.name, it.arguments))
        },
    )

    private fun AgentMessageRole.toOpenRouterRole(): String = when (this) {
        AgentMessageRole.SYSTEM -> "system"
        AgentMessageRole.USER -> "user"
        AgentMessageRole.ASSISTANT -> "assistant"
        AgentMessageRole.TOOL -> "tool"
    }

    private fun parseAssistantMessage(responseText: String): AgentLlmResult = runCatching {
        val choices = json.parseToJsonElement(responseText).jsonObject["choices"] as? JsonArray
            ?: return@runCatching AgentLlmResult.InvalidResponse(
                "OpenRouter response did not contain an assistant message.",
            )
        val assistantMessageJson = choices.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?: return@runCatching AgentLlmResult.InvalidResponse(
                "OpenRouter response did not contain an assistant message.",
            )

        val content = when (val messageContent = assistantMessageJson["content"]) {
            is JsonPrimitive -> if (messageContent.isString) messageContent.content else ""
            else -> ""
        }
        AgentLlmResult.Message(
            AgentMessage(
                role = AgentMessageRole.ASSISTANT,
                content = content,
                toolCalls = assistantMessageJson["tool_calls"]?.jsonArray
                    ?.mapNotNull { toolCallJson ->
                        val functionJson = toolCallJson.jsonObject["function"]?.jsonObject
                        val id = toolCallJson.jsonObject["id"]?.jsonPrimitive?.content.orEmpty()
                        val name = functionJson?.get("name")?.jsonPrimitive?.content.orEmpty()
                        val arguments = functionJson?.get("arguments")?.jsonPrimitive?.content.orEmpty()
                        AgentToolCall(id, name, arguments)
                    }
                    ?: emptyList(),
            ),
        )
    }.getOrElse { AgentLlmResult.InvalidResponse("OpenRouter response did not contain an assistant message.") }

    private fun toResult(response: Response): AgentLlmResult {
        val responseText = response.body?.string().orEmpty()
        val responseSummary = response.summaryForLogs()
        if (!response.isSuccessful) {
            val reason = extractErrorMessage(responseText)
            debugLogger.warn(
                "response.remoteFailure $responseSummary reason=${reason.compactForLog()} " +
                    "bodyPreview=${responseText.previewForLog()}",
            )
            return when (response.code) {
                429 -> AgentLlmResult.RateLimited(reason)
                else -> AgentLlmResult.RemoteFailure(response.code, reason)
            }
        }

        return parseAssistantMessage(responseText)
    }

    private fun extractErrorMessage(responseText: String): String = runCatching {
        json.parseToJsonElement(responseText).jsonObject["error"]
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.content
            ?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: "OpenRouter request failed."

    private fun Response.summaryForLogs(): String =
        "code=$code message=${message.compactForLog()} requestId=${header("x-request-id").orEmpty().ifBlank { "missing" }}"

    private fun String.compactForLog(): String = replace(Regex("\\s+"), " ").trim()

    private fun String.previewForLog(maxChars: Int = 500): String = compactForLog().let { compact ->
        if (compact.length <= maxChars) compact else compact.take(maxChars) + "...<truncated>"
    }

    private companion object {
        const val DEFAULT_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<ChatTool>,
    @SerialName("tool_choice")
    val toolChoice: ToolChoice? = null,
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    val name: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ChatToolCall>? = null,
)

@Serializable
private data class ChatTool(val type: String, val function: ToolFunction)

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
private data class ToolChoiceFunction(val name: String)

@Serializable
private data class ChatToolCall(
    val id: String,
    val type: String,
    val function: ToolCallFunction,
)

@Serializable
private data class ToolCallFunction(
    val name: String,
    val arguments: String,
)
