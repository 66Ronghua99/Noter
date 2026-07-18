package com.cory.noter.ai

import com.cory.noter.agent.AgentLlmGateway
import com.cory.noter.agent.AgentLlmRequest
import com.cory.noter.agent.AgentLlmResult
import com.cory.noter.agent.AgentMessage
import com.cory.noter.agent.AgentMessageRole
import com.cory.noter.agent.AgentToolChoice
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RetryingAgentLlmGatewayTest {
    @Test
    fun `retryable service failure gets one bounded retry and honors retry after`() = runTest {
        val delegate = RecordingGateway(
            mutableListOf(
                AgentLlmResult.ServiceFailure("rate_limited", retryable = true, retryAfterSeconds = 3),
                AgentLlmResult.Message(AgentMessage(AgentMessageRole.ASSISTANT, "done")),
            ),
        )
        val delays = mutableListOf<Long>()
        val gateway = RetryingAgentLlmGateway(delegate, delayProvider = { delays += it })

        val result = gateway.complete(request())

        assertThat(result).isEqualTo(AgentLlmResult.Message(AgentMessage(AgentMessageRole.ASSISTANT, "done")))
        assertThat(delegate.requests).hasSize(2)
        assertThat(delays).containsExactly(3_000L)
    }

    @Test
    fun `nonretryable and exhausted failures are returned without another attempt`() = runTest {
        val unauthorized = RecordingGateway(
            mutableListOf(AgentLlmResult.ServiceFailure("unauthorized", retryable = false)),
        )
        val unauthorizedDelays = mutableListOf<Long>()
        val unauthorizedGateway = RetryingAgentLlmGateway(
            unauthorized,
            delayProvider = { unauthorizedDelays += it },
        )
        assertThat(unauthorizedGateway.complete(request()))
            .isEqualTo(AgentLlmResult.ServiceFailure("unauthorized", retryable = false))
        assertThat(unauthorized.requests).hasSize(1)
        assertThat(unauthorizedDelays).isEmpty()

        val exhausted = RecordingGateway(
            mutableListOf(
                AgentLlmResult.NetworkFailure("first"),
                AgentLlmResult.NetworkFailure("second"),
            ),
        )
        val exhaustedGateway = RetryingAgentLlmGateway(
            exhausted,
            maxAttempts = 2,
            delayProvider = {},
        )
        assertThat(exhaustedGateway.complete(request()))
            .isEqualTo(AgentLlmResult.NetworkFailure("second"))
        assertThat(exhausted.requests).hasSize(2)
    }

    private fun request() = AgentLlmRequest(
        messages = listOf(AgentMessage(AgentMessageRole.USER, "wake me")),
        tools = emptyList(),
        toolChoice = AgentToolChoice.Auto,
    )

    private class RecordingGateway(
        private val results: MutableList<AgentLlmResult>,
    ) : AgentLlmGateway {
        val requests = mutableListOf<AgentLlmRequest>()

        override suspend fun complete(request: AgentLlmRequest): AgentLlmResult {
            requests += request
            return results.removeAt(0)
        }
    }
}
