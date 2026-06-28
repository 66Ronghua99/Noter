package com.cory.noter.ai

import com.cory.noter.agent.AgentLlmRequest
import com.cory.noter.agent.AgentLlmResult
import com.cory.noter.agent.AgentMessage
import com.cory.noter.agent.AgentMessageRole
import com.cory.noter.agent.AgentToolCall
import com.cory.noter.agent.AgentToolChoice
import com.cory.noter.agent.AgentToolSpec
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test

class OpenRouterAgentClientTest {
    @Test
    fun `production source does not keep legacy single purpose openrouter path`() {
        val forbiddenTokens = listOf(
            "submit_alarm_draft",
            "OpenRouterGateway",
            "OpenRouterResult",
            "AiAlarmResponseParser",
        )

        val matches = File("src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val source = file.readText()
                forbiddenTokens.asSequence()
                    .filter { token -> source.contains(token) }
                    .map { token -> "${file.relativeTo(File(".")).path}:$token" }
            }
            .toList()

        assertThat(matches).isEmpty()
    }

    @Test
    fun `complete posts generic messages tools and required create_alarm choice`() = runTest {
        var capturedBody = ""
        val client = OpenRouterAgentClient(
            callFactory = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val buffer = Buffer()
                    chain.request().body!!.writeTo(buffer)
                    capturedBody = buffer.readUtf8()
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
                                    "content": "",
                                    "tool_calls": [
                                      {
                                        "id": "call-1",
                                        "type": "function",
                                        "function": {
                                          "name": "create_alarm",
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

        val result = client.complete(
            AgentLlmRequest(
                apiKey = "sk-or-v1-test",
                modelId = "deepseek/deepseek-v4-flash",
                messages = listOf(
                    AgentMessage(AgentMessageRole.SYSTEM, "Create alarms only."),
                    AgentMessage(AgentMessageRole.USER, "tomorrow at 8"),
                ),
                tools = listOf(
                    AgentToolSpec(
                        "create_alarm",
                        "Create an alarm.",
                        buildJsonObject { put("type", "object") },
                    ),
                ),
                toolChoice = AgentToolChoice.Required("create_alarm"),
            ),
        )

        assertThat(result).isEqualTo(
            AgentLlmResult.Message(
                AgentMessage(
                    role = AgentMessageRole.ASSISTANT,
                    content = "",
                    toolCalls = listOf(AgentToolCall("call-1", "create_alarm", "{\"title\":\"Take medicine\"}")),
                ),
            ),
        )
        val body = Json.parseToJsonElement(capturedBody).jsonObject
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo("deepseek/deepseek-v4-flash")
        assertThat(body["tools"]!!.jsonArray.single().jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            .isEqualTo("create_alarm")
        assertThat(body["parallel_tool_calls"]!!.jsonPrimitive.content).isEqualTo("false")
        assertThat(capturedBody).doesNotContain("submit_alarm_draft")
        assertThat(body["tool_choice"]!!.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            .isEqualTo("create_alarm")
    }

    @Test
    fun `complete maps required any tool choice to openrouter required string`() = runTest {
        var capturedBody = ""
        val client = OpenRouterAgentClient(
            callFactory = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val buffer = Buffer()
                    chain.request().body!!.writeTo(buffer)
                    capturedBody = buffer.readUtf8()
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
                                    "content": "",
                                    "tool_calls": [
                                      {
                                        "id": "call-1",
                                        "type": "function",
                                        "function": {
                                          "name": "create_alarm",
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

        val result = client.complete(
            basicRequest(toolChoice = AgentToolChoice.RequiredAnyTool),
        )

        assertThat(result).isEqualTo(
            AgentLlmResult.Message(
                AgentMessage(
                    role = AgentMessageRole.ASSISTANT,
                    content = "",
                    toolCalls = listOf(AgentToolCall("call-1", "create_alarm", "{\"title\":\"Take medicine\"}")),
                ),
            ),
        )
        val body = Json.parseToJsonElement(capturedBody).jsonObject
        assertThat(body["tool_choice"]!!.jsonPrimitive.content).isEqualTo("required")
        assertThat(body["parallel_tool_calls"]!!.jsonPrimitive.content).isEqualTo("false")
    }

    @Test
    fun `tool follow up messages preserve tool metadata and required choice`() = runTest {
        var capturedBody = ""
        val client = OpenRouterAgentClient(
            callFactory = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val buffer = Buffer()
                    chain.request().body!!.writeTo(buffer)
                    capturedBody = buffer.readUtf8()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"choices":[{"message":{"role":"assistant","content":"Done."}}]}""".toResponseBody("application/json".toMediaType()))
                        .build()
                })
                .build(),
        )

        val result = client.complete(
            AgentLlmRequest(
                apiKey = "sk-or-v1-test",
                modelId = "deepseek/deepseek-v4-flash",
                messages = listOf(
                    AgentMessage(AgentMessageRole.USER, "Run the tool."),
                    AgentMessage(
                        role = AgentMessageRole.TOOL,
                        content = "{\"title\":\"Take medicine\"}",
                        toolCallId = "call-1",
                        toolName = "create_alarm",
                    ),
                ),
                tools = listOf(
                    AgentToolSpec(
                        "create_alarm",
                        "Create an alarm.",
                        buildJsonObject { put("type", "object") },
                    ),
                ),
                toolChoice = AgentToolChoice.RequiredAnyTool,
            ),
        )

        assertThat(result).isEqualTo(
            AgentLlmResult.Message(AgentMessage(AgentMessageRole.ASSISTANT, "Done.")),
        )

        val body = Json.parseToJsonElement(capturedBody).jsonObject
        assertThat(body["tool_choice"]!!.jsonPrimitive.content).isEqualTo("required")
        assertThat(body["parallel_tool_calls"]!!.jsonPrimitive.content).isEqualTo("false")
        assertThat(body["tools"]!!.jsonArray.single().jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            .isEqualTo("create_alarm")
        val messages = body["messages"]!!.jsonArray
        assertThat(messages[1].jsonObject["role"]!!.jsonPrimitive.content).isEqualTo("tool")
        assertThat(messages[1].jsonObject["tool_call_id"]!!.jsonPrimitive.content).isEqualTo("call-1")
        assertThat(messages[1].jsonObject["name"]!!.jsonPrimitive.content).isEqualTo("create_alarm")
    }

    @Test
    fun `assistant tool role is preserved instead of rewritten to assistant`() = runTest {
        val client = responseClient(
            """
            {
              "choices": [
                {
                  "message": {
                    "role": "tool",
                    "content": "tool result"
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val result = client.complete(basicRequest())

        assertThat(result).isEqualTo(
            AgentLlmResult.Message(AgentMessage(AgentMessageRole.TOOL, "tool result")),
        )
    }

    @Test
    fun `missing message in successful response is invalid`() = runTest {
        val client = responseClient("""{"choices":[{}]}""")

        val result = client.complete(basicRequest())

        assertThat(result).isEqualTo(
            AgentLlmResult.InvalidResponse("OpenRouter response did not contain an assistant message."),
        )
    }

    @Test
    fun `unsupported role in successful response is invalid`() = runTest {
        val client = responseClient(
            """
            {
              "choices": [
                {
                  "message": {
                    "role": "critic",
                    "content": "nope"
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val result = client.complete(basicRequest())

        assertThat(result).isEqualTo(
            AgentLlmResult.InvalidResponse("OpenRouter response did not contain an assistant message."),
        )
    }

    @Test
    fun `non string content in successful response is invalid`() = runTest {
        val client = responseClient(
            """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": 123
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val result = client.complete(basicRequest())

        assertThat(result).isEqualTo(
            AgentLlmResult.InvalidResponse("OpenRouter response did not contain an assistant message."),
        )
    }

    @Test
    fun `tool call with missing required fields is invalid`() = runTest {
        val client = responseClient(
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
                          "name": "create_alarm"
                        }
                      }
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val result = client.complete(basicRequest())

        assertThat(result).isEqualTo(
            AgentLlmResult.InvalidResponse("OpenRouter response did not contain an assistant message."),
        )
    }

    @Test
    fun `network failure returns network failure`() = runTest {
        val client = OpenRouterAgentClient(
            callFactory = OkHttpClient.Builder()
                .addInterceptor {
                    throw IOException("socket timeout")
                }
                .build(),
        )

        val result = client.complete(basicRequest())

        assertThat(result).isEqualTo(
            AgentLlmResult.NetworkFailure("socket timeout"),
        )
    }

    @Test
    fun `rate limit returns rate limited failure`() = runTest {
        val client = responseClient(
            """
            {
              "error": {
                "message": "Rate limit exceeded for model."
              }
            }
            """.trimIndent(),
            code = 429,
            message = "Too Many Requests",
        )

        val result = client.complete(basicRequest())

        assertThat(result).isEqualTo(
            AgentLlmResult.RateLimited("Rate limit exceeded for model."),
        )
    }

    @Test
    fun `remote failure returns remote failure`() = runTest {
        val client = responseClient(
            """
            {
              "error": {
                "message": "Upstream exploded."
              }
            }
            """.trimIndent(),
            code = 502,
            message = "Bad Gateway",
        )

        val result = client.complete(basicRequest())

        assertThat(result).isEqualTo(
            AgentLlmResult.RemoteFailure(502, "Upstream exploded."),
        )
    }
}

private fun basicRequest(
    toolChoice: AgentToolChoice = AgentToolChoice.Required("create_alarm"),
): AgentLlmRequest = AgentLlmRequest(
    apiKey = "sk-or-v1-test",
    modelId = "deepseek/deepseek-v4-flash",
    messages = listOf(AgentMessage(AgentMessageRole.USER, "tomorrow at 8")),
    tools = listOf(
        AgentToolSpec(
            "create_alarm",
            "Create an alarm.",
            buildJsonObject { put("type", "object") },
        ),
    ),
    toolChoice = toolChoice,
)

private fun responseClient(
    body: String,
    code: Int = 200,
    message: String = "OK",
): OpenRouterAgentClient = OpenRouterAgentClient(
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
