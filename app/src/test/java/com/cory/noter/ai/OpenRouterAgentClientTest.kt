package com.cory.noter.ai

import com.cory.noter.agent.AgentLlmRequest
import com.cory.noter.agent.AgentLlmResult
import com.cory.noter.agent.AgentMessage
import com.cory.noter.agent.AgentMessageRole
import com.cory.noter.agent.AgentToolCall
import com.cory.noter.agent.AgentToolChoice
import com.cory.noter.agent.AgentToolSpec
import com.google.common.truth.Truth.assertThat
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
        assertThat(capturedBody).doesNotContain("submit_alarm_draft")
        assertThat(body["tool_choice"]!!.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            .isEqualTo("create_alarm")
    }

    @Test
    fun `assistant content without tool calls maps to message not alarm parser failure`() = runTest {
        val client = OpenRouterAgentClient(
            callFactory = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"choices":[{"message":{"role":"assistant","content":"Created."}}]}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build(),
        )

        val result = client.complete(basicRequest())

        assertThat(result).isEqualTo(
            AgentLlmResult.Message(AgentMessage(AgentMessageRole.ASSISTANT, "Created.")),
        )
    }
}

private fun basicRequest(): AgentLlmRequest = AgentLlmRequest(
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
    toolChoice = AgentToolChoice.Required("create_alarm"),
)
