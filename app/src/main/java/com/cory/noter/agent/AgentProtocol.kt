package com.cory.noter.agent

import kotlinx.serialization.json.JsonObject

data class AgentLoopConfig(
    val maxModelTurns: Int = 2,
    val maxToolExecutions: Int = 1,
)

enum class AgentMessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}

data class AgentMessage(
    val role: AgentMessageRole,
    val content: String,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolCalls: List<AgentToolCall> = emptyList(),
)

data class AgentToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val risk: AgentToolRisk = AgentToolRisk.READ,
    val endsRun: Boolean = false,
)

enum class AgentToolRisk {
    READ,
    WRITE,
    DESTRUCTIVE,
    BATCH,
}

data class AgentToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class AgentToolResult(
    val toolCallId: String,
    val toolName: String,
    val content: JsonObject,
    val committed: Boolean,
)

sealed interface AgentToolExecution {
    data class Success(
        val result: AgentToolResult,
    ) : AgentToolExecution

    data class Failure(
        val failure: AgentFailure,
        val committedResult: AgentToolResult? = null,
    ) : AgentToolExecution
}

interface AgentTool {
    val spec: AgentToolSpec

    suspend fun execute(call: AgentToolCall): AgentToolExecution
}

class AgentToolRegistry(
    tools: List<AgentTool>,
) {
    private val byName: Map<String, AgentTool> = tools.associateBy { it.spec.name }
    val specs: List<AgentToolSpec> = tools.map { it.spec }

    fun get(name: String): AgentTool? = byName[name]
}

sealed interface AgentToolChoice {
    data object Auto : AgentToolChoice

    data object RequiredAnyTool : AgentToolChoice

    data class Required(val toolName: String) : AgentToolChoice
}

data class AgentRunRequest(
    val apiKey: String,
    val modelId: String,
    val initialMessages: List<AgentMessage>,
    val toolRegistry: AgentToolRegistry,
    val toolChoice: AgentToolChoice = AgentToolChoice.Auto,
)

data class AgentLlmRequest(
    val apiKey: String,
    val modelId: String,
    val messages: List<AgentMessage>,
    val tools: List<AgentToolSpec>,
    val toolChoice: AgentToolChoice,
)

sealed interface AgentLlmResult {
    data class Message(val message: AgentMessage) : AgentLlmResult

    data class NetworkFailure(val reason: String) : AgentLlmResult

    data class RateLimited(val reason: String) : AgentLlmResult

    data class RemoteFailure(val code: Int, val reason: String) : AgentLlmResult

    data class InvalidResponse(val reason: String) : AgentLlmResult
}

interface AgentLlmGateway {
    suspend fun complete(request: AgentLlmRequest): AgentLlmResult
}

sealed interface AgentRunResult {
    data class Completed(
        val finalMessage: AgentMessage,
        val toolResults: List<AgentToolResult>,
    ) : AgentRunResult

    data class CompletedWithFinalizationFailure(
        val committedResults: List<AgentToolResult>,
        val failure: AgentFailure,
    ) : AgentRunResult

    data class FailedAfterToolResults(
        val toolResults: List<AgentToolResult>,
        val failure: AgentFailure,
    ) : AgentRunResult

    data class Failed(
        val failure: AgentFailure,
    ) : AgentRunResult
}

sealed interface AgentFailure {
    data class MissingToolCall(val reason: String) : AgentFailure

    data class ToolNotRegistered(val toolName: String) : AgentFailure

    data class ToolExecutionFailed(val reason: String) : AgentFailure

    data class ClarificationRequired(val reason: String) : AgentFailure

    data class CreateFailed(val reason: String) : AgentFailure

    data class NetworkFailure(val reason: String) : AgentFailure

    data class RateLimited(val reason: String) : AgentFailure

    data class ModelFailure(val reason: String) : AgentFailure

    data class RemoteFailure(val code: Int, val reason: String) : AgentFailure

    data class ToolLimitExceeded(val reason: String) : AgentFailure

    data class ModelTurnLimitExceeded(val reason: String) : AgentFailure
}
