package com.cory.noter.ui

import com.cory.noter.agent.AgentLlmGateway
import com.cory.noter.agent.AgentLlmRequest
import com.cory.noter.agent.AgentLlmResult
import com.cory.noter.ai.OpenRouterGateway
import com.cory.noter.ai.OpenRouterResult

class FakeAgentLlmGateway : AgentLlmGateway {
    val requests = mutableListOf<AgentLlmRequest>()
    val results = mutableListOf<AgentLlmResult>()

    override suspend fun complete(request: AgentLlmRequest): AgentLlmResult {
        requests += request
        check(results.isNotEmpty()) { "No fake AgentLlmResult queued." }
        return results.removeAt(0)
    }
}

class FakeOpenRouterGateway : OpenRouterGateway {
    val requests = mutableListOf<Request>()
    var nextResult: OpenRouterResult = OpenRouterResult.Success("{}")

    override suspend fun createChatCompletion(
        apiKey: String,
        modelId: String,
        prompt: String,
    ): OpenRouterResult {
        requests += Request(
            apiKey = apiKey,
            modelId = modelId,
            prompt = prompt,
        )
        return nextResult
    }

    data class Request(
        val apiKey: String,
        val modelId: String,
        val prompt: String,
    )
}
