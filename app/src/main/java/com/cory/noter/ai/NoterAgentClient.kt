package com.cory.noter.ai

import com.cory.noter.BuildConfig
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class NoterAgentClient(
    private val callFactory: Call.Factory = defaultNoterCallFactory(),
    private val baseUrl: String = BuildConfig.NOTER_API_BASE_URL,
    private val clientToken: String = BuildConfig.NOTER_CLIENT_TOKEN,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AgentLlmGateway {
    override suspend fun complete(request: AgentLlmRequest): AgentLlmResult =
        suspendCancellableCoroutine { continuation ->
            val requestBody = json.encodeToString(
                AgentCompletionRequest.serializer(),
                request.toPublicRequest(),
            )
            val httpRequest = runCatching {
                Request.Builder()
                    .url(endpoint("/api/v1/agent/completions"))
                    .header("Authorization", "Bearer $clientToken")
                    .header("Content-Type", "application/json")
                    .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            }.getOrElse {
                continuation.resume(
                    AgentLlmResult.ServiceFailure(NoterApiFailureCode.SERVICE_UNAVAILABLE.wireValue, true),
                )
                return@suspendCancellableCoroutine
            }

            val call = runCatching { callFactory.newCall(httpRequest) }.getOrElse {
                continuation.resume(
                    AgentLlmResult.ServiceFailure(NoterApiFailureCode.NETWORK_UNAVAILABLE.wireValue, true),
                )
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resume(
                                AgentLlmResult.ServiceFailure(
                                    code = NoterApiFailureCode.NETWORK_UNAVAILABLE.wireValue,
                                    retryable = true,
                                ),
                            )
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = response.use { it.toAgentResult() }
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }
                },
            )
        }

    private fun Response.toAgentResult(): AgentLlmResult {
        val responseText = body?.string().orEmpty()
        if (!isSuccessful) {
            val failure = parseNoterApiFailure(code, responseText, header("Retry-After"), json)
            return AgentLlmResult.ServiceFailure(
                code = failure.code.wireValue,
                retryable = failure.retryable,
                retryAfterSeconds = failure.retryAfterSeconds,
            )
        }

        return runCatching {
            val responseJson = json.parseToJsonElement(responseText).jsonObject
            val messageJson = responseJson["message"]?.jsonObject ?: error("missing message")
            val roleJson = messageJson["role"] as? JsonPrimitive
            val role = roleJson?.takeIf { it.isString }?.content ?: error("missing role")
            val content = when (val contentJson = messageJson["content"]) {
                null, JsonNull -> ""
                is JsonPrimitive -> if (contentJson.isString) contentJson.content else error("invalid content")
                else -> error("invalid content")
            }
            val toolCalls = messageJson["toolCalls"]?.jsonArray?.map { callJson ->
                val call = callJson.jsonObject
                val idJson = call["id"] as? JsonPrimitive
                val nameJson = call["name"] as? JsonPrimitive
                val argumentsJson = call["arguments"] as? JsonPrimitive
                AgentToolCall(
                    id = idJson?.takeIf { it.isString }?.content ?: error("missing tool call ID"),
                    name = nameJson?.takeIf { it.isString }?.content ?: error("missing tool name"),
                    arguments = argumentsJson?.takeIf { it.isString }?.content
                        ?: error("missing tool arguments"),
                )
            }.orEmpty()
            if (role != "assistant" || (content.isBlank() && toolCalls.isEmpty())) {
                error("invalid assistant message")
            }
            AgentLlmResult.Message(
                AgentMessage(
                    role = AgentMessageRole.ASSISTANT,
                    content = content,
                    toolCalls = toolCalls,
                ),
            )
        }.getOrElse {
            AgentLlmResult.ServiceFailure(
                code = NoterApiFailureCode.INVALID_SERVICE_RESPONSE.wireValue,
                retryable = false,
            )
        }
    }

    private fun endpoint(path: String): String = baseUrl.trimEnd('/') + path

    private fun AgentLlmRequest.toPublicRequest(): AgentCompletionRequest = AgentCompletionRequest(
        messages = messages.map { it.toPublicMessage() },
        tools = tools.map {
            AgentToolPayload(
                name = it.name,
                description = it.description,
                parameters = it.parameters,
            )
        },
        toolChoice = when (val choice = toolChoice) {
            AgentToolChoice.Auto -> ToolChoicePayload(mode = "auto")
            AgentToolChoice.RequiredAnyTool -> ToolChoicePayload(mode = "required")
            is AgentToolChoice.Required -> ToolChoicePayload(mode = "named", toolName = choice.toolName)
        },
    )

    private fun AgentMessage.toPublicMessage(): AgentMessagePayload = AgentMessagePayload(
        role = role.name.lowercase(),
        content = content,
        toolCallId = toolCallId,
        toolName = toolName,
        toolCalls = toolCalls.takeIf { it.isNotEmpty() }?.map {
            AgentToolCallPayload(id = it.id, name = it.name, arguments = it.arguments)
        },
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

internal fun defaultNoterCallFactory(): Call.Factory = okhttp3.OkHttpClient.Builder().build()

@Serializable
private data class AgentCompletionRequest(
    val messages: List<AgentMessagePayload>,
    val tools: List<AgentToolPayload>,
    val toolChoice: ToolChoicePayload,
)

@Serializable
private data class AgentMessagePayload(
    val role: String,
    val content: String,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolCalls: List<AgentToolCallPayload>? = null,
)

@Serializable
private data class AgentToolCallPayload(
    val id: String,
    val name: String,
    val arguments: String,
)

@Serializable
private data class AgentToolPayload(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
private data class ToolChoicePayload(
    val mode: String,
    val toolName: String? = null,
)
