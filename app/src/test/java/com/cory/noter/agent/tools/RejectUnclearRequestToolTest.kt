package com.cory.noter.agent.tools

import com.cory.noter.agent.AgentFailure
import com.cory.noter.agent.AgentToolCall
import com.cory.noter.agent.AgentToolExecution
import com.cory.noter.agent.AgentToolRisk
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class RejectUnclearRequestToolTest {
    @Test
    fun `tool exposes non committing reject schema`() {
        val tool = RejectUnclearRequestTool()

        assertThat(tool.spec.name).isEqualTo("reject_unclear_request")
        assertThat(tool.spec.risk).isEqualTo(AgentToolRisk.READ)
        assertThat(tool.spec.parameters["type"]!!.jsonPrimitive.content).isEqualTo("object")
        val properties = tool.spec.parameters["properties"]!!.jsonObject
        assertThat(properties.keys).containsExactly("reason", "retryHint")
    }

    @Test
    fun `valid arguments return structured non committing result`() = runTest {
        val tool = RejectUnclearRequestTool()

        val result = tool.execute(
            AgentToolCall(
                id = "call-reject",
                name = "reject_unclear_request",
                arguments = """
                    {
                      "reason": "I couldn't tell which day to use.",
                      "retryHint": "Try saying a day and time."
                    }
                """.trimIndent(),
            ),
        )

        assertThat(result).isInstanceOf(AgentToolExecution.Success::class.java)
        val success = result as AgentToolExecution.Success
        assertThat(success.result.committed).isFalse()
        assertThat(success.result.toolCallId).isEqualTo("call-reject")
        assertThat(success.result.toolName).isEqualTo("reject_unclear_request")
        assertThat(success.result.content["status"]!!.jsonPrimitive.content).isEqualTo("rejected")
        assertThat(success.result.content["reason"]!!.jsonPrimitive.content)
            .isEqualTo("I couldn't tell which day to use.")
        assertThat(success.result.content["retryHint"]!!.jsonPrimitive.content)
            .isEqualTo("Try saying a day and time.")
    }

    @Test
    fun `blank reason fails before producing tool result`() = runTest {
        val tool = RejectUnclearRequestTool()

        val result = tool.execute(
            AgentToolCall(
                id = "call-reject",
                name = "reject_unclear_request",
                arguments = """{"reason":"   ","retryHint":"Try again."}""",
            ),
        )

        assertThat(result).isEqualTo(
            AgentToolExecution.Failure(
                AgentFailure.ToolExecutionFailed("reject_unclear_request requires a nonblank reason."),
            ),
        )
    }
}
