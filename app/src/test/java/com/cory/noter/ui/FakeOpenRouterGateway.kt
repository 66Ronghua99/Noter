package com.cory.noter.ui

import com.cory.noter.ai.OpenRouterGateway
import com.cory.noter.ai.OpenRouterResult

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
