package com.cory.noter.agent.tools

import com.cory.noter.agent.AgentToolCall
import com.cory.noter.agent.AgentToolExecution
import com.cory.noter.agent.AgentToolRisk
import com.cory.noter.agent.AgentFailure
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class EndTaskToolTest {
    @Test
    fun `tool exposes non committing end schema`() {
        val tool = EndTaskTool()

        assertThat(tool.spec.name).isEqualTo("end_task")
        assertThat(tool.spec.risk).isEqualTo(AgentToolRisk.READ)
        assertThat(tool.spec.parameters["type"]!!.jsonPrimitive.content).isEqualTo("object")
        val properties = tool.spec.parameters["properties"]!!.jsonObject
        assertThat(properties.keys).containsExactly("reason")
    }

    @Test
    fun `valid arguments return structured non committing result`() = runTest {
        val tool = EndTaskTool()

        val result = tool.execute(
            AgentToolCall(
                id = "call-end",
                name = "end_task",
                arguments = """{"reason":"Alarm created."}""",
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        assertThat(success.result.committed).isFalse()
        assertThat(success.result.toolCallId).isEqualTo("call-end")
        assertThat(success.result.toolName).isEqualTo("end_task")
        assertThat(success.result.content["status"]!!.jsonPrimitive.content).isEqualTo("ended")
        assertThat(success.result.content["reason"]!!.jsonPrimitive.content).isEqualTo("Alarm created.")
    }

    @Test
    fun `empty arguments are accepted`() = runTest {
        val tool = EndTaskTool()

        val result = tool.execute(
            AgentToolCall(
                id = "call-end",
                name = "end_task",
                arguments = "{}",
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        assertThat(success.result.content["status"]!!.jsonPrimitive.content).isEqualTo("ended")
        assertThat(success.result.content).doesNotContainKey("reason")
    }

    @Test
    fun `invalid json fails before producing tool result`() = runTest {
        val tool = EndTaskTool()

        val result = tool.execute(
            AgentToolCall(
                id = "call-end",
                name = "end_task",
                arguments = "{",
            ),
        )

        assertThat(result).isEqualTo(
            AgentToolExecution.Failure(
                AgentFailure.ToolExecutionFailed("Invalid JSON"),
            ),
        )
    }
}
