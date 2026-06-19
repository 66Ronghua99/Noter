package com.cory.noter.ai

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Timeout
import org.junit.Test

class OpenRouterClientTest {
    @Test
    fun `create chat completion posts bearer auth selected model prompt and alarm draft tool`() = runTest {
        var capturedRequestUrl = ""
        var capturedAuthorization = ""
        var capturedContentType = ""
        var capturedBody = ""
        val client = OpenRouterClient(
            callFactory = OkHttpClient.Builder()
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
                                    "tool_calls": [
                                      {
                                        "type": "function",
                                        "function": {
                                          "name": "submit_alarm_draft",
                                          "arguments": "{\"title\":\"Take medicine\"}"
                                        }
                                      }
                                    ]
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
            modelId = OpenRouterModel.DefaultId,
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
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo(OpenRouterModel.DefaultId)
        val messages = body["messages"]!!.jsonArray
        assertThat(messages).hasSize(1)
        assertThat(messages[0].jsonObject["role"]!!.jsonPrimitive.content).isEqualTo("user")
        assertThat(messages[0].jsonObject["content"]!!.jsonPrimitive.content)
            .isEqualTo("tomorrow at 8 remind me to take medicine")
        val tools = body["tools"]!!.jsonArray
        assertThat(tools).hasSize(1)
        val tool = tools[0].jsonObject
        assertThat(tool["type"]!!.jsonPrimitive.content).isEqualTo("function")
        val function = tool["function"]!!.jsonObject
        assertThat(function["name"]!!.jsonPrimitive.content).isEqualTo("submit_alarm_draft")
        val parameters = function["parameters"]!!.jsonObject
        assertThat(parameters["type"]!!.jsonPrimitive.content).isEqualTo("object")
        assertThat(parameters["required"]!!.jsonArray.map { it.jsonPrimitive.content })
            .containsExactly(
                "title",
                "hour",
                "minute",
                "repeatRule",
                "date",
                "confidence",
                "needsClarification",
                "clarificationReason",
            )
            .inOrder()
        val repeatRuleProperties = parameters["properties"]!!
            .jsonObject["repeatRule"]!!
            .jsonObject["properties"]!!
            .jsonObject
        assertThat(
            repeatRuleProperties["type"]!!
                .jsonObject["enum"]!!
                .jsonArray
                .map { it.jsonPrimitive.content },
        ).containsExactly(
            "once",
            "daily",
            "weekdays",
            "custom_weekdays",
            "weekly_interval",
        ).inOrder()
        assertThat(repeatRuleProperties.keys).containsAtLeast(
            "startDate",
            "endDate",
            "intervalWeeks",
        )
        assertThat(
            body["tool_choice"]!!
                .jsonObject["function"]!!
                .jsonObject["name"]!!
                .jsonPrimitive
                .content,
        ).isEqualTo("submit_alarm_draft")
    }

    @Test
    fun `successful tool call returns function arguments`() = runTest {
        val client = OpenRouterClient(
            callFactory = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
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
                                    "tool_calls": [
                                      {
                                        "type": "function",
                                        "function": {
                                          "name": "submit_alarm_draft",
                                          "arguments": "{\"title\":\"Take medicine\",\"hour\":8,\"minute\":30,\"repeatRule\":{\"type\":\"once\",\"daysOfWeek\":[]},\"date\":\"2026-04-24\",\"confidence\":0.92,\"needsClarification\":false,\"clarificationReason\":\"\"}"
                                        }
                                      }
                                    ]
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
            modelId = "deepseek/deepseek-v4-flash",
            prompt = "tomorrow at 8 remind me to take medicine",
        )

        assertThat(result).isEqualTo(
            OpenRouterResult.Success(
                "{\"title\":\"Take medicine\",\"hour\":8,\"minute\":30,\"repeatRule\":{\"type\":\"once\",\"daysOfWeek\":[]},\"date\":\"2026-04-24\",\"confidence\":0.92,\"needsClarification\":false,\"clarificationReason\":\"\"}",
            ),
        )
    }

    @Test
    fun `assistant content without tool call returns invalid response`() = runTest {
        val client = OpenRouterClient(
            callFactory = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
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
            modelId = OpenRouterModel.DefaultId,
            prompt = "tomorrow at 8 remind me to take medicine",
        )

        assertThat(result).isEqualTo(
            OpenRouterResult.InvalidResponse(
                "OpenRouter response did not contain a submit_alarm_draft tool call.",
            ),
        )
    }

    @Test
    fun `network exception returns network failure`() = runTest {
        val client = OpenRouterClient(
            callFactory = OkHttpClient.Builder()
                .addInterceptor {
                    throw IOException("socket timeout")
                }
                .build(),
        )

        val result = client.createChatCompletion(
            apiKey = "sk-or-v1-test",
            modelId = OpenRouterModel.DefaultId,
            prompt = "hello",
        )

        assertThat(result).isInstanceOf(OpenRouterResult.NetworkFailure::class.java)
        assertThat((result as OpenRouterResult.NetworkFailure).reason).contains("socket timeout")
    }

    @Test
    fun `default call factory has bounded total timeout`() {
        val callFactory = defaultOpenRouterCallFactory()

        assertThat((callFactory as OkHttpClient).callTimeoutMillis).isEqualTo(60_000)
    }

    @Test
    fun `too many requests returns rate limited failure`() = runTest {
        val client = OpenRouterClient(
            callFactory = OkHttpClient.Builder()
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
            modelId = OpenRouterModel.DefaultId,
            prompt = "hello",
        )

        assertThat(result).isEqualTo(
            OpenRouterResult.RateLimited("Rate limit exceeded for model."),
        )
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `cancelling coroutine cancels in flight http call`() = runTest(timeout = 5_000.milliseconds) {
        val callFactory = RecordingCallFactory()
        val client = OpenRouterClient(callFactory = callFactory)

        val job = launch {
            client.createChatCompletion(
                apiKey = "sk-or-v1-test",
                modelId = OpenRouterModel.DefaultId,
                prompt = "hello",
            )
        }
        advanceUntilIdle()

        assertThat(callFactory.call.wasEnqueued).isTrue()

        job.cancelAndJoin()

        assertThat(callFactory.call.cancelCalled).isTrue()
    }

    private class RecordingCallFactory : Call.Factory {
        val call = RecordingCall()

        override fun newCall(request: Request): Call {
            call.request = request
            return call
        }
    }

    private class RecordingCall : Call {
        var request: Request? = null
        var wasEnqueued: Boolean = false
        var cancelCalled: Boolean = false

        override fun request(): Request = requireNotNull(request)

        override fun execute(): Response {
            error("execute should not be used")
        }

        override fun enqueue(responseCallback: Callback) {
            wasEnqueued = true
        }

        override fun cancel() {
            cancelCalled = true
        }

        override fun isExecuted(): Boolean = wasEnqueued

        override fun isCanceled(): Boolean = cancelCalled

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = RecordingCall().also {
            it.request = request
        }

        override fun <T : Any> tag(type: KClass<T>): T? = null

        override fun <T> tag(type: Class<out T>): T? = null

        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T =
            computeIfAbsent()

        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T =
            computeIfAbsent()
    }
}
