package com.cory.noter.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class AgentLoopRunner(
    private val gateway: AgentLlmGateway,
    private val config: AgentLoopConfig = AgentLoopConfig(),
) {
    suspend fun run(request: AgentRunRequest): AgentRunResult {
        require(config.maxModelTurns > 0) { "maxModelTurns must be greater than zero" }
        require(config.maxToolExecutions >= 0) { "maxToolExecutions must not be negative" }

        when (val toolChoice = request.toolChoice) {
            is AgentToolChoice.Required -> {
                if (request.toolRegistry.get(toolChoice.toolName) == null) {
                    return AgentRunResult.Failed(AgentFailure.ToolNotRegistered(toolChoice.toolName))
                }
            }

            AgentToolChoice.Auto -> Unit
        }

        val messages = request.initialMessages.toMutableList()
        val toolResults = mutableListOf<AgentToolResult>()
        var modelTurns = 0
        var toolExecutions = 0

        while (modelTurns < config.maxModelTurns) {
            modelTurns += 1
            val llmResult = gateway.complete(
                AgentLlmRequest(
                    apiKey = request.apiKey,
                    modelId = request.modelId,
                    messages = messages.toList(),
                    tools = request.toolRegistry.specs,
                    toolChoice = request.toolChoice,
                ),
            )

            val assistantMessage = when (llmResult) {
                is AgentLlmResult.Message -> llmResult.message
                is AgentLlmResult.NetworkFailure -> return committedOrFailed(toolResults, AgentFailure.NetworkFailure(llmResult.reason))
                is AgentLlmResult.RateLimited -> return committedOrFailed(toolResults, AgentFailure.RateLimited(llmResult.reason))
                is AgentLlmResult.RemoteFailure -> return committedOrFailed(toolResults, AgentFailure.RemoteFailure(llmResult.code, llmResult.reason))
                is AgentLlmResult.InvalidResponse -> return committedOrFailed(toolResults, AgentFailure.ModelFailure(llmResult.reason))
            }

            if (assistantMessage.role != AgentMessageRole.ASSISTANT) {
                return committedOrFailed(
                    toolResults,
                    AgentFailure.ModelFailure("Model message role must be ASSISTANT."),
                )
            }
            messages += assistantMessage

            if (assistantMessage.toolCalls.isEmpty()) {
                return if (toolResults.isEmpty()) {
                    AgentRunResult.Failed(AgentFailure.MissingToolCall("Model did not call a tool."))
                } else {
                    AgentRunResult.Completed(
                        finalMessage = assistantMessage,
                        toolResults = toolResults.toList(),
                    )
                }
            }

            for (toolCall in assistantMessage.toolCalls) {
                if (toolExecutions >= config.maxToolExecutions) {
                    return committedOrFailed(toolResults, AgentFailure.ToolLimitExceeded("Tool execution limit exceeded."))
                }
                val tool = request.toolRegistry.get(toolCall.name)
                    ?: return committedOrFailed(toolResults, AgentFailure.ToolNotRegistered(toolCall.name))

                when (val execution = tool.execute(toolCall)) {
                    is AgentToolExecution.Success -> {
                        toolExecutions += 1
                        toolResults += execution.result
                        messages += execution.result.toToolMessage()
                    }

                    is AgentToolExecution.Failure -> {
                        execution.committedResult?.let { toolResults += it }
                        return committedOrFailed(toolResults, execution.failure)
                    }
                }
            }
        }

        return committedOrFailed(
            committedResults = toolResults,
            failure = AgentFailure.ModelTurnLimitExceeded("Model turn limit exceeded."),
        )
    }

    private fun committedOrFailed(
        committedResults: List<AgentToolResult>,
        failure: AgentFailure,
    ): AgentRunResult {
        val committed = committedResults.filter { it.committed }
        return if (committed.isNotEmpty()) {
            AgentRunResult.CompletedWithFinalizationFailure(committed, failure)
        } else {
            AgentRunResult.Failed(failure)
        }
    }

    private fun AgentToolResult.toToolMessage(): AgentMessage = AgentMessage(
        role = AgentMessageRole.TOOL,
        content = Json.encodeToString(JsonObject.serializer(), content),
        toolCallId = toolCallId,
        toolName = toolName,
    )
}
