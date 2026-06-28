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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class EndTaskTool(
    private val json: Json = Json,
) : AgentTool {
    override val spec: AgentToolSpec = AgentToolSpec(
        name = Name,
        description = "End the alarm-agent task after the needed action has been completed or rejected.",
        parameters = endParameters(),
        risk = AgentToolRisk.READ,
        endsRun = true,
    )

    override suspend fun execute(call: AgentToolCall): AgentToolExecution {
        val arguments = runCatching { json.parseToJsonElement(call.arguments).jsonObject }
            .getOrElse {
                return AgentToolExecution.Failure(
                    AgentFailure.ToolExecutionFailed("Invalid JSON"),
                )
            }
        val reason = arguments.optionalNonBlankString("reason")

        return AgentToolExecution.Success(
            AgentToolResult(
                toolCallId = call.id,
                toolName = spec.name,
                content = buildJsonObject {
                    put("status", "ended")
                    reason?.let { put("reason", it) }
                },
                committed = false,
            ),
        )
    }

    private fun endParameters(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonObject("properties") {
            putJsonObject("reason") {
                put("type", "string")
                put("description", "Optional brief reason that the task is complete.")
            }
        }
    }

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

    companion object {
        const val Name = "end_task"
    }
}
