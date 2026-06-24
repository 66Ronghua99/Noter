package com.cory.noter.ai

import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.ByteString.Companion.toByteString

class OpenRouterAsrRequest(
    val apiKey: String,
    val modelId: String,
    val audioBytes: ByteArray,
)

sealed interface AsrTranscriptionResult {
    data class Transcribed(val text: String) : AsrTranscriptionResult

    data class NetworkFailure(val reason: String) : AsrTranscriptionResult

    data class RateLimited(val reason: String) : AsrTranscriptionResult

    data class RemoteFailure(val code: Int, val reason: String) : AsrTranscriptionResult

    data class InvalidResponse(val reason: String) : AsrTranscriptionResult
}

class OpenRouterAsrClient(
    private val callFactory: Call.Factory = defaultOpenRouterCallFactory(),
    private val endpoint: String = OPEN_ROUTER_AUDIO_TRANSCRIPTIONS_ENDPOINT,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val debugLogger: OpenRouterDebugLogger = NoOpOpenRouterDebugLogger,
) {
    suspend fun transcribe(request: OpenRouterAsrRequest): AsrTranscriptionResult =
        suspendCancellableCoroutine { continuation ->
            val requestBody = json.encodeToString(AsrTranscriptionRequest.serializer(), request.toPayload())
            val httpRequest = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer ${request.apiKey}")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody(OPEN_ROUTER_JSON_MEDIA_TYPE))
                .build()

            debugLogger.debug(
                "asr.request.start endpoint=$endpoint model=${request.modelId} audioBytes=${request.audioBytes.size}",
            )

            try {
                val call = callFactory.newCall(httpRequest)
                continuation.invokeOnCancellation {
                    debugLogger.debug("asr.request.cancelled model=${request.modelId}")
                    call.cancel()
                }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            if (continuation.isActive) {
                                debugLogger.warn(
                                    "asr.request.networkFailure type=${e.javaClass.simpleName} " +
                                        "message=${e.message.orEmpty()}",
                                    e,
                                )
                                continuation.resume(
                                    AsrTranscriptionResult.NetworkFailure(
                                        e.message ?: "OpenRouter ASR request failed.",
                                    ),
                                )
                            }
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val result = response.use { it.toResult() }
                            if (continuation.isActive) {
                                continuation.resume(result)
                            }
                        }
                    },
                )
            } catch (error: IOException) {
                debugLogger.warn(
                    "asr.request.createFailure type=${error.javaClass.simpleName} " +
                        "message=${error.message.orEmpty()}",
                    error,
                )
                continuation.resume(
                    AsrTranscriptionResult.NetworkFailure(
                        error.message ?: "OpenRouter ASR request failed.",
                    ),
                )
            }
        }

    private fun OpenRouterAsrRequest.toPayload(): AsrTranscriptionRequest = AsrTranscriptionRequest(
        model = modelId,
        inputAudio = AsrInputAudio(
            data = audioBytes.toByteString().base64(),
            format = "m4a",
        ),
    )

    private fun Response.toResult(): AsrTranscriptionResult {
        val responseText = body?.string().orEmpty()
        val responseSummary = summaryForLogs()
        if (!isSuccessful) {
            val reason = extractErrorMessage(responseText)
            debugLogger.warn(
                "asr.response.remoteFailure $responseSummary reason=${reason.compactForLog()} " +
                    "bodyPreview=${responseText.previewForLog()}",
            )
            return when (code) {
                429 -> AsrTranscriptionResult.RateLimited(reason)
                else -> AsrTranscriptionResult.RemoteFailure(code, reason)
            }
        }

        return parseTranscript(responseText)
    }

    private fun parseTranscript(responseText: String): AsrTranscriptionResult = runCatching {
        val responseJson = json.parseToJsonElement(responseText).jsonObject
        val transcript = responseJson.requiredTranscriptText()
        AsrTranscriptionResult.Transcribed(transcript)
    }.getOrElse { invalidTranscriptResponse() }

    private fun extractErrorMessage(responseText: String): String = runCatching {
        json.parseToJsonElement(responseText).jsonObject["error"]
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.content
            ?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: "OpenRouter ASR request failed."

    private fun JsonObject.requiredTranscriptText(): String {
        val primitive = this["text"] as? JsonPrimitive ?: throw invalidTranscriptResponseException()
        if (!primitive.isString) {
            throw invalidTranscriptResponseException()
        }
        return primitive.content.trim().takeIf { it.isNotBlank() }
            ?: throw invalidTranscriptResponseException()
    }

    private fun invalidTranscriptResponse(): AsrTranscriptionResult.InvalidResponse =
        AsrTranscriptionResult.InvalidResponse("OpenRouter ASR response did not contain transcript text.")

    private fun invalidTranscriptResponseException(): IllegalArgumentException =
        IllegalArgumentException("OpenRouter ASR response did not contain transcript text.")

    private fun Response.summaryForLogs(): String =
        "code=$code message=${message.compactForLog()} requestId=${header("x-request-id").orEmpty().ifBlank { "missing" }}"

    private fun String.compactForLog(): String = replace(Regex("\\s+"), " ").trim()

    private fun String.previewForLog(maxChars: Int = 500): String = compactForLog().let { compact ->
        if (compact.length <= maxChars) compact else compact.take(maxChars) + "...<truncated>"
    }
}

@Serializable
private data class AsrTranscriptionRequest(
    val model: String,
    @SerialName("input_audio")
    val inputAudio: AsrInputAudio,
)

@Serializable
private data class AsrInputAudio(
    val data: String,
    val format: String,
)
