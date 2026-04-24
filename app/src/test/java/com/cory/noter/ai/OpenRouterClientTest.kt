package com.cory.noter.ai

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test

class OpenRouterClientTest {
    @Test
    fun `create chat completion posts bearer auth selected model and prompt`() = runTest {
        var capturedRequestUrl = ""
        var capturedAuthorization = ""
        var capturedContentType = ""
        var capturedBody = ""
        val client = OpenRouterClient(
            httpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val request = chain.request()
                    capturedRequestUrl = request.url.toString()
                    capturedAuthorization = request.header("Authorization").orEmpty()
                    capturedContentType = request.header("Content-Type").orEmpty()
                    val buffer = Buffer()
                    request.body!!.writeTo(buffer)
                    capturedBody = buffer.readUtf8()

                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """
                            {
                              "choices": [
                                {
                                  "message": {
                                    "role": "assistant",
                                    "content": "{\"title\":\"Take medicine\"}"
                                  }
                                }
                              ]
                            }
                            """.trimIndent().toResponseBody("application/json".toMediaType()),
                        )
                        .build()
                })
                .build(),
        )

        val result = client.createChatCompletion(
            apiKey = "sk-or-v1-test",
            modelId = "openrouter/free",
            prompt = "tomorrow at 8 remind me to take medicine",
        )

        assertThat(result).isEqualTo(
            OpenRouterResult.Success("{\"title\":\"Take medicine\"}"),
        )
        assertThat(capturedRequestUrl)
            .isEqualTo("https://openrouter.ai/api/v1/chat/completions")
        assertThat(capturedAuthorization).isEqualTo("Bearer sk-or-v1-test")
        assertThat(capturedContentType).contains("application/json")
        val body = Json.parseToJsonElement(capturedBody).jsonObject
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo("openrouter/free")
        val messages = body["messages"]!!.jsonArray
        assertThat(messages).hasSize(1)
        assertThat(messages[0].jsonObject["role"]!!.jsonPrimitive.content).isEqualTo("user")
        assertThat(messages[0].jsonObject["content"]!!.jsonPrimitive.content)
            .isEqualTo("tomorrow at 8 remind me to take medicine")
    }

    @Test
    fun `network exception returns network failure`() = runTest {
        val client = OpenRouterClient(
            httpClient = OkHttpClient.Builder()
                .addInterceptor {
                    throw IOException("socket timeout")
                }
                .build(),
        )

        val result = client.createChatCompletion(
            apiKey = "sk-or-v1-test",
            modelId = "openrouter/free",
            prompt = "hello",
        )

        assertThat(result).isInstanceOf(OpenRouterResult.NetworkFailure::class.java)
        assertThat((result as OpenRouterResult.NetworkFailure).reason).contains("socket timeout")
    }

    @Test
    fun `too many requests returns rate limited failure`() = runTest {
        val client = OpenRouterClient(
            httpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("Too Many Requests")
                        .body(
                            """
                            {
                              "error": {
                                "message": "Rate limit exceeded for model."
                              }
                            }
                            """.trimIndent().toResponseBody("application/json".toMediaType()),
                        )
                        .build()
                })
                .build(),
        )

        val result = client.createChatCompletion(
            apiKey = "sk-or-v1-test",
            modelId = "openrouter/free",
            prompt = "hello",
        )

        assertThat(result).isEqualTo(
            OpenRouterResult.RateLimited("Rate limit exceeded for model."),
        )
    }
}
