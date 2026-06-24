package com.cory.noter.agent.tools

import com.cory.noter.agent.AgentFailure
import com.cory.noter.agent.AgentTool
import com.cory.noter.agent.AgentToolCall
import com.cory.noter.agent.AgentToolExecution
import com.cory.noter.agent.AgentToolResult
import com.cory.noter.agent.AgentToolRisk
import com.cory.noter.agent.AgentToolSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class RejectUnclearRequestTool(
    private val json: Json = Json,
) : AgentTool {
    override val spec: AgentToolSpec = AgentToolSpec(
        name = "reject_unclear_request",
        description = "Reject an unclear, incomplete, non-alarm, or poor-transcript request without creating an alarm.",
        parameters = rejectParameters(),
        risk = AgentToolRisk.READ,
    )

    override suspend fun execute(call: AgentToolCall): AgentToolExecution {
        val arguments = runCatching { json.parseToJsonElement(call.arguments).jsonObject }
            .getOrElse {
                return AgentToolExecution.Failure(
                    AgentFailure.ToolExecutionFailed("Invalid JSON"),
                )
            }
        val reason = arguments.requiredNonBlankString("reason")
            ?: return AgentToolExecution.Failure(
                AgentFailure.ToolExecutionFailed("reject_unclear_request requires a nonblank reason."),
            )
        val retryHint = arguments.optionalNonBlankString("retryHint")

        return AgentToolExecution.Success(
            AgentToolResult(
                toolCallId = call.id,
                toolName = spec.name,
                content = buildJsonObject {
                    put("status", "rejected")
                    put("reason", reason)
                    retryHint?.let { put("retryHint", it) }
                },
                committed = false,
            ),
        )
    }

    private fun rejectParameters(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonObject("properties") {
            putJsonObject("reason") {
                put("type", "string")
                put("description", "Brief user-facing reason the request cannot safely create an alarm.")
            }
            putJsonObject("retryHint") {
                put("type", "string")
                put("description", "Optional short suggestion for what the user should say next.")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("reason")) })
    }

    private fun JsonObject.requiredNonBlankString(name: String): String? =
        (this[name] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun JsonObject.optionalNonBlankString(name: String): String? {
        val value = this[name] ?: return null
        if (value == JsonNull) {
            return null
        }
        return (value as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
