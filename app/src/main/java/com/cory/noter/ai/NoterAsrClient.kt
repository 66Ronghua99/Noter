package com.cory.noter.ai

import com.cory.noter.BuildConfig
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class NoterAsrRequest(
    val languageCode: String?,
    val audioBytes: ByteArray,
)

sealed interface NoterAsrResult {
    data class Transcribed(val text: String) : NoterAsrResult

    data class ServiceFailure(
        val code: NoterApiFailureCode,
        val retryable: Boolean,
        val retryAfterSeconds: Long? = null,
    ) : NoterAsrResult
}

interface NoterAsrGateway {
    suspend fun transcribe(request: NoterAsrRequest): NoterAsrResult
}

class NoterAsrClient(
    private val callFactory: Call.Factory = defaultNoterCallFactory(),
    private val baseUrl: String = BuildConfig.NOTER_API_BASE_URL,
    private val clientToken: String = BuildConfig.NOTER_CLIENT_TOKEN,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : NoterAsrGateway {
    override suspend fun transcribe(request: NoterAsrRequest): NoterAsrResult =
        suspendCancellableCoroutine { continuation ->
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "audio",
                    "recording.m4a",
                    request.audioBytes.toRequestBody(AUDIO_MEDIA_TYPE),
                )
                .apply {
                    request.languageCode?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        addFormDataPart("language", it)
                    }
                }
                .build()
            val httpRequest = runCatching {
                Request.Builder()
                    .url(baseUrl.trimEnd('/') + "/api/v1/asr/transcriptions")
                    .header("Authorization", "Bearer $clientToken")
                    .post(body)
                    .build()
            }.getOrElse {
                continuation.resume(
                    NoterAsrResult.ServiceFailure(NoterApiFailureCode.SERVICE_UNAVAILABLE, true),
                )
                return@suspendCancellableCoroutine
            }
            val call = runCatching { callFactory.newCall(httpRequest) }.getOrElse {
                continuation.resume(
                    NoterAsrResult.ServiceFailure(NoterApiFailureCode.NETWORK_UNAVAILABLE, true),
                )
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resume(
                                NoterAsrResult.ServiceFailure(NoterApiFailureCode.NETWORK_UNAVAILABLE, true),
                            )
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = response.use { it.toAsrResult() }
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }
                },
            )
        }

    private fun Response.toAsrResult(): NoterAsrResult {
        val responseText = body?.string().orEmpty()
        if (!isSuccessful) {
            val failure = parseNoterApiFailure(code, responseText, header("Retry-After"), json)
            return NoterAsrResult.ServiceFailure(
                failure.code,
                failure.retryable,
                failure.retryAfterSeconds,
            )
        }
        return runCatching {
            val textJson = json.parseToJsonElement(responseText).jsonObject["text"]
            val text = (textJson as? kotlinx.serialization.json.JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: error("missing transcript")
            NoterAsrResult.Transcribed(text)
        }.getOrElse {
            NoterAsrResult.ServiceFailure(NoterApiFailureCode.INVALID_SERVICE_RESPONSE, false)
        }
    }

    private companion object {
        val AUDIO_MEDIA_TYPE = "audio/mp4".toMediaType()
    }
}
