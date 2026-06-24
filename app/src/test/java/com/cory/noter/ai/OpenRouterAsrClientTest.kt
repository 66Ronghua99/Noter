package com.cory.noter.ai

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ByteString.Companion.toByteString
import org.junit.Test

class OpenRouterAsrClientTest {
    @Test
    fun `transcribe posts selected model and base64 audio payload to audio transcriptions endpoint`() = runTest {
        var capturedUrl = ""
        var capturedAuthorization = ""
        var capturedBody = ""
        val audioBytes = "fake-audio".encodeToByteArray()
        val client = OpenRouterAsrClient(
            callFactory = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    capturedUrl = chain.request().url.toString()
                    capturedAuthorization = chain.request().header("Authorization").orEmpty()
                    val buffer = Buffer()
                    chain.request().body!!.writeTo(buffer)
                    capturedBody = buffer.readUtf8()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"text":"wake me at eight"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                })
                .build(),
        )

        val result = client.transcribe(
            OpenRouterAsrRequest(
                apiKey = "sk-or-v1-test",
                modelId = AsrModel.builtInIds[1],
                audioBytes = audioBytes,
            ),
        )

        assertThat(result).isEqualTo(AsrTranscriptionResult.Transcribed("wake me at eight"))
        assertThat(capturedUrl).isEqualTo("https://openrouter.ai/api/v1/audio/transcriptions")
        assertThat(capturedAuthorization).isEqualTo("Bearer sk-or-v1-test")
        val body = Json.parseToJsonElement(capturedBody).jsonObject
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo(AsrModel.builtInIds[1])
        assertThat(body["audio"]!!.jsonPrimitive.content).isEqualTo(audioBytes.toByteString().base64())
    }

    @Test
    fun `valid transcript response maps to transcript text`() = runTest {
        val client = responseClient("""{"text":"turn on the alarm"}""")

        val result = client.transcribe(basicAsrRequest())

        assertThat(result).isEqualTo(AsrTranscriptionResult.Transcribed("turn on the alarm"))
    }

    @Test
    fun `network failure maps to explicit network failure`() = runTest {
        val client = OpenRouterAsrClient(
            callFactory = OkHttpClient.Builder()
                .addInterceptor {
                    throw IOException("socket timeout")
                }
                .build(),
        )

        val result = client.transcribe(basicAsrRequest())

        assertThat(result).isEqualTo(AsrTranscriptionResult.NetworkFailure("socket timeout"))
    }

    @Test
    fun `rate limit maps to explicit rate limited failure`() = runTest {
        val client = responseClient(
            """
            {
              "error": {
                "message": "ASR rate limit exceeded."
              }
            }
            """.trimIndent(),
            code = 429,
            message = "Too Many Requests",
        )

        val result = client.transcribe(basicAsrRequest())

        assertThat(result).isEqualTo(AsrTranscriptionResult.RateLimited("ASR rate limit exceeded."))
    }

    @Test
    fun `non successful remote response maps to explicit remote failure`() = runTest {
        val client = responseClient(
            """
            {
              "error": {
                "message": "ASR upstream exploded."
              }
            }
            """.trimIndent(),
            code = 502,
            message = "Bad Gateway",
        )

        val result = client.transcribe(basicAsrRequest())

        assertThat(result).isEqualTo(AsrTranscriptionResult.RemoteFailure(502, "ASR upstream exploded."))
    }

    @Test
    fun `malformed successful response maps to invalid response`() = runTest {
        val client = responseClient("""{"choices":[]}""")

        val result = client.transcribe(basicAsrRequest())

        assertThat(result).isEqualTo(
            AsrTranscriptionResult.InvalidResponse("OpenRouter ASR response did not contain transcript text."),
        )
    }

    @Test
    fun `blank transcript maps to invalid response`() = runTest {
        val client = responseClient("""{"text":"   "}""")

        val result = client.transcribe(basicAsrRequest())

        assertThat(result).isEqualTo(
            AsrTranscriptionResult.InvalidResponse("OpenRouter ASR response did not contain transcript text."),
        )
    }

    @Test
    fun `remote failure does not silently switch selected asr model`() = runTest {
        var callCount = 0
        var capturedBody = ""
        val selectedModelId = "mistralai/voxtral-mini-transcribe"
        val client = OpenRouterAsrClient(
            callFactory = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    callCount += 1
                    val buffer = Buffer()
                    chain.request().body!!.writeTo(buffer)
                    capturedBody = buffer.readUtf8()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(500)
                        .message("Internal Server Error")
                        .body("""{"error":{"message":"selected model failed"}}""".toResponseBody("application/json".toMediaType()))
                        .build()
                })
                .build(),
        )

        val result = client.transcribe(
            basicAsrRequest(modelId = selectedModelId),
        )

        assertThat(result).isEqualTo(AsrTranscriptionResult.RemoteFailure(500, "selected model failed"))
        assertThat(callCount).isEqualTo(1)
        val body = Json.parseToJsonElement(capturedBody).jsonObject
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo(selectedModelId)
    }
}

private fun basicAsrRequest(
    modelId: String = AsrModel.DefaultId,
): OpenRouterAsrRequest = OpenRouterAsrRequest(
    apiKey = "sk-or-v1-test",
    modelId = modelId,
    audioBytes = "audio-bytes".encodeToByteArray(),
)

private fun responseClient(
    body: String,
    code: Int = 200,
    message: String = "OK",
): OpenRouterAsrClient = OpenRouterAsrClient(
    callFactory = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(message)
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        })
        .build(),
)
