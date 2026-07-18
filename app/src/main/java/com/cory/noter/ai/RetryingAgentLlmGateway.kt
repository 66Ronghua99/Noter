package com.cory.noter.ai

import com.cory.noter.agent.AgentLlmGateway
import com.cory.noter.agent.AgentLlmRequest
import com.cory.noter.agent.AgentLlmResult
import kotlinx.coroutines.delay

/** Owns the bounded foreground retry policy for one logical model request. */
class RetryingAgentLlmGateway(
    private val delegate: AgentLlmGateway,
    private val maxAttempts: Int = 2,
    private val delayProvider: suspend (Long) -> Unit = { delay(it) },
    private val defaultRetryDelayMillis: Long = 1_000L,
) : AgentLlmGateway {
    init {
        require(maxAttempts > 0) { "maxAttempts must be greater than zero" }
        require(defaultRetryDelayMillis >= 0) { "defaultRetryDelayMillis must not be negative" }
    }

    override suspend fun complete(request: AgentLlmRequest): AgentLlmResult {
        var attempt = 1
        while (true) {
            val result = delegate.complete(request)
            if (!result.isRetryable() || attempt >= maxAttempts) {
                return result
            }
            val retryAfterMillis = when (result) {
                is AgentLlmResult.ServiceFailure -> result.retryAfterSeconds
                    ?.coerceAtLeast(0L)
                    ?.times(1_000L)

                else -> null
            }
            delayProvider(retryAfterMillis ?: defaultRetryDelayMillis)
            attempt += 1
        }
    }

    private fun AgentLlmResult.isRetryable(): Boolean = when (this) {
        is AgentLlmResult.NetworkFailure,
        is AgentLlmResult.RateLimited,
        -> true

        is AgentLlmResult.RemoteFailure -> code in 500..599
        is AgentLlmResult.ServiceFailure -> retryable
        is AgentLlmResult.InvalidResponse,
        is AgentLlmResult.Message,
        -> false
    }
}
