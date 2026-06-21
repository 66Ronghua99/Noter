package com.cory.noter.ui

import com.cory.noter.agent.AgentLlmGateway
import com.cory.noter.agent.AgentLlmRequest
import com.cory.noter.agent.AgentLlmResult

class FakeAgentLlmGateway : AgentLlmGateway {
    val requests = mutableListOf<AgentLlmRequest>()
    val results = mutableListOf<AgentLlmResult>()

    override suspend fun complete(request: AgentLlmRequest): AgentLlmResult {
        requests += request
        check(results.isNotEmpty()) { "No fake AgentLlmResult queued." }
        return results.removeAt(0)
    }
}
