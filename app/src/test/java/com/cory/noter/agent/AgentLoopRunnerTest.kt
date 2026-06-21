package com.cory.noter.agent

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

class AgentLoopRunnerTest {
    @Test
    fun `runner executes tool then sends tool result before final assistant message`() = runTest {
        val gateway = RecordingGateway(
            AgentLlmResult.Message(
                AgentMessage(
                    role = AgentMessageRole.ASSISTANT,
                    content = "",
                    toolCalls = listOf(AgentToolCall("call-1", "create_alarm", """{"title":"Take medicine"}""")),
                ),
            ),
            AgentLlmResult.Message(
                AgentMessage(AgentMessageRole.ASSISTANT, "Created."),
            ),
        )
        val tool = RecordingTool(
            AgentToolSpec("create_alarm", "Create an alarm.", buildJsonObject { put("type", "object") }),
            AgentToolExecution.Success(
                AgentToolResult(
                    toolCallId = "call-1",
                    toolName = "create_alarm",
                    content = buildJsonObject {
                        put("status", "created")
                        put("alarmId", 42)
                    },
                    committed = true,
                ),
            ),
        )
        val runner = AgentLoopRunner(gateway)

        val result = runner.run(
            AgentRunRequest(
                apiKey = "sk-test",
                modelId = "deepseek/deepseek-v4-flash",
                initialMessages = listOf(AgentMessage(AgentMessageRole.USER, "tomorrow at 8")),
                toolRegistry = AgentToolRegistry(listOf(tool)),
                toolChoice = AgentToolChoice.Required("create_alarm"),
            ),
        )

        assertThat(result).isInstanceOf(AgentRunResult.Completed::class.java)
        assertThat((result as AgentRunResult.Completed).finalMessage.content).isEqualTo("Created.")
        assertThat(tool.calls.single().name).isEqualTo("create_alarm")
        assertThat(gateway.requests).hasSize(2)
        assertThat(gateway.requests[0].tools.single().name).isEqualTo("create_alarm")
        assertThat(gateway.requests[1].messages.last().role).isEqualTo(AgentMessageRole.TOOL)
        assertThat(gateway.requests[1].messages.last().toolCallId).isEqualTo("call-1")
    }

    @Test
    fun `runner fails when first model response has no tool call`() = runTest {
        val runner = AgentLoopRunner(
            RecordingGateway(
                AgentLlmResult.Message(AgentMessage(AgentMessageRole.ASSISTANT, "I cannot do that.")),
            ),
        )

        val result = runner.run(basicRequest())

        assertThat(result).isEqualTo(
            AgentRunResult.Failed(AgentFailure.MissingToolCall("Model did not call a tool.")),
        )
    }

    @Test
    fun `runner does not execute another tool after global tool limit`() = runTest {
        val gateway = RecordingGateway(
            AgentLlmResult.Message(
                AgentMessage(
                    role = AgentMessageRole.ASSISTANT,
                    content = "",
                    toolCalls = listOf(AgentToolCall("call-1", "create_alarm", "{}")),
                ),
            ),
            AgentLlmResult.Message(
                AgentMessage(
                    role = AgentMessageRole.ASSISTANT,
                    content = "",
                    toolCalls = listOf(AgentToolCall("call-2", "create_alarm", "{}")),
                ),
            ),
        )
        val tool = RecordingTool(
            AgentToolSpec("create_alarm", "Create an alarm.", buildJsonObject { put("type", "object") }),
            AgentToolExecution.Success(
                AgentToolResult(
                    toolCallId = "call-1",
                    toolName = "create_alarm",
                    content = buildJsonObject { put("status", "created") },
                    committed = true,
                ),
            ),
        )

        val result = AgentLoopRunner(gateway).run(
            basicRequest(toolRegistry = AgentToolRegistry(listOf(tool))),
        )

        assertThat(tool.calls.map { it.id }).containsExactly("call-1")
        assertThat(result).isInstanceOf(AgentRunResult.CompletedWithFinalizationFailure::class.java)
        assertThat((result as AgentRunResult.CompletedWithFinalizationFailure).committedResults).hasSize(1)
        assertThat(result.failure).isEqualTo(AgentFailure.ToolLimitExceeded("Tool execution limit exceeded."))
    }

    @Test
    fun `runner fails when requested tool is not registered`() = runTest {
        val gateway = RecordingGateway(
            AgentLlmResult.Message(
                AgentMessage(AgentMessageRole.ASSISTANT, "unused"),
            ),
        )

        val result = AgentLoopRunner(gateway).run(
            basicRequest(
                toolRegistry = AgentToolRegistry(emptyList()),
                toolChoice = AgentToolChoice.Required("missing_tool"),
            ),
        )

        assertThat(result).isEqualTo(AgentRunResult.Failed(AgentFailure.ToolNotRegistered("missing_tool")))
        assertThat(gateway.requests).isEmpty()
    }

    @Test
    fun `runner rejects non assistant final message and keeps committed tool result`() = runTest {
        val gateway = RecordingGateway(
            AgentLlmResult.Message(
                AgentMessage(
                    role = AgentMessageRole.ASSISTANT,
                    content = "",
                    toolCalls = listOf(AgentToolCall("call-1", "create_alarm", """{"title":"Take medicine"}""")),
                ),
            ),
            AgentLlmResult.Message(
                AgentMessage(AgentMessageRole.USER, "Created."),
            ),
        )
        val tool = RecordingTool(
            AgentToolSpec("create_alarm", "Create an alarm.", buildJsonObject { put("type", "object") }),
            AgentToolExecution.Success(
                AgentToolResult(
                    toolCallId = "call-1",
                    toolName = "create_alarm",
                    content = buildJsonObject {
                        put("status", "created")
                        put("alarmId", 42)
                    },
                    committed = true,
                ),
            ),
        )

        val result = AgentLoopRunner(gateway).run(
            basicRequest(toolRegistry = AgentToolRegistry(listOf(tool))),
        )

        assertThat(result).isInstanceOf(AgentRunResult.CompletedWithFinalizationFailure::class.java)
        assertThat((result as AgentRunResult.CompletedWithFinalizationFailure).committedResults).hasSize(1)
        assertThat(result.failure).isEqualTo(AgentFailure.ModelFailure("Model message role must be ASSISTANT."))
        assertThat(gateway.requests).hasSize(2)
        assertThat(gateway.requests[1].messages.last().role).isEqualTo(AgentMessageRole.TOOL)
        assertThat(gateway.requests[1].messages.last().toolCallId).isEqualTo("call-1")
    }

    private fun basicRequest(
        toolRegistry: AgentToolRegistry = AgentToolRegistry(
            listOf(
                RecordingTool(
                    AgentToolSpec("create_alarm", "Create an alarm.", buildJsonObject { put("type", "object") }),
                    AgentToolExecution.Success(
                        AgentToolResult(
                            "call-1",
                            "create_alarm",
                            buildJsonObject { put("status", "created") },
                            committed = true,
                        ),
                    ),
                ),
            ),
        ),
        toolChoice: AgentToolChoice = AgentToolChoice.Required("create_alarm"),
    ): AgentRunRequest = AgentRunRequest(
        apiKey = "sk-test",
        modelId = "deepseek/deepseek-v4-flash",
        initialMessages = listOf(AgentMessage(AgentMessageRole.USER, "tomorrow at 8")),
        toolRegistry = toolRegistry,
        toolChoice = toolChoice,
    )
}

private class RecordingGateway(
    private vararg val results: AgentLlmResult,
) : AgentLlmGateway {
    val requests = mutableListOf<AgentLlmRequest>()
    private var index = 0

    override suspend fun complete(request: AgentLlmRequest): AgentLlmResult {
        requests += request
        return results[index++]
    }
}

private class RecordingTool(
    override val spec: AgentToolSpec,
    private val result: AgentToolExecution,
) : AgentTool {
    val calls = mutableListOf<AgentToolCall>()

    override suspend fun execute(call: AgentToolCall): AgentToolExecution {
        calls += call
        return result
    }
}
