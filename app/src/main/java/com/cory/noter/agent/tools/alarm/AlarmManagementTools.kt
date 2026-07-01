package com.cory.noter.agent.tools.alarm

import com.cory.noter.agent.AgentFailure
import com.cory.noter.agent.AgentTool
import com.cory.noter.agent.AgentToolCall
import com.cory.noter.agent.AgentToolExecution
import com.cory.noter.agent.AgentToolResult
import com.cory.noter.agent.AgentToolRisk
import com.cory.noter.agent.AgentToolSpec
import com.cory.noter.alarm.AlarmManagementResult
import com.cory.noter.alarm.AlarmManagementUseCase
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmPauseMode
import com.cory.noter.domain.alarm.RepeatRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class ListAlarmsTool(
    private val alarmRepository: AlarmRepository,
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : AgentTool {
    override val spec: AgentToolSpec = AgentToolSpec(
        name = Name,
        description = "List the user's alarms with ids and pause state before managing an uncertain target.",
        parameters = emptyObjectParameters(),
        risk = AgentToolRisk.READ,
    )

    override suspend fun execute(call: AgentToolCall): AgentToolExecution {
        val zoneId = zoneIdProvider()
        val alarms = alarmRepository.alarms.first()
        return AgentToolExecution.Success(
            AgentToolResult(
                toolCallId = call.id,
                toolName = spec.name,
                content = buildJsonObject {
                    put("status", "listed_alarms")
                    putJsonArray("alarms") {
                        alarms.sortedBy { it.id }.forEach { alarm ->
                            add(alarm.toJson(zoneId))
                        }
                    }
                },
                committed = false,
            ),
        )
    }

    private fun Alarm.toJson(zoneId: ZoneId): JsonObject = buildJsonObject {
        put("id", id)
        put("title", title)
        put("localTime", "%02d:%02d".format(hour, minute))
        put("repeatSummary", repeatRule.toSummary())
        val trigger = nextTriggerAtMillis
        if (trigger == null) {
            put("nextTriggerAtMillis", JsonNull)
        } else {
            put("nextTriggerAtMillis", trigger)
            put(
                "nextTriggerLocal",
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(Instant.ofEpochMilli(trigger).atZone(zoneId)),
            )
        }
        put("pauseState", pauseMode.toToolValue())
    }

    companion object {
        const val Name = "list_alarms"
    }
}

class PauseAlarmTool(
    private val managementUseCase: AlarmManagementUseCase,
    private val parser: AlarmManagementArgumentsParser = AlarmManagementArgumentsParser(),
) : AgentTool {
    override val spec: AgentToolSpec = AgentToolSpec(
        name = Name,
        description = "Pause one alarm by id for its next occurrence or indefinitely.",
        parameters = pauseParameters(),
        risk = AgentToolRisk.WRITE,
    )

    override suspend fun execute(call: AgentToolCall): AgentToolExecution {
        val arguments = parser.parsePause(call.arguments).getOrElse { error ->
            return AgentToolExecution.Failure(
                AgentFailure.ToolExecutionFailed(error.message ?: "Invalid pause_alarm arguments."),
            )
        }

        val result = when (arguments.mode) {
            PauseMode.NEXT_OCCURRENCE -> managementUseCase.pauseNextOccurrence(arguments.alarmId)
            PauseMode.INDEFINITE -> managementUseCase.pauseIndefinitely(arguments.alarmId)
        }
        return AgentToolExecution.Success(
            result.toToolResult(
                toolCallId = call.id,
                toolName = spec.name,
                requestedAlarmId = arguments.alarmId,
                successStatus = when (arguments.mode) {
                    PauseMode.NEXT_OCCURRENCE -> "paused_next_occurrence"
                    PauseMode.INDEFINITE -> "paused_indefinitely"
                },
            ),
        )
    }

    companion object {
        const val Name = "pause_alarm"
    }
}

class ResumeAlarmTool(
    private val managementUseCase: AlarmManagementUseCase,
    private val parser: AlarmManagementArgumentsParser = AlarmManagementArgumentsParser(),
) : AgentTool {
    override val spec: AgentToolSpec = AgentToolSpec(
        name = Name,
        description = "Resume one paused alarm by id.",
        parameters = alarmIdParameters(),
        risk = AgentToolRisk.WRITE,
    )

    override suspend fun execute(call: AgentToolCall): AgentToolExecution {
        val alarmId = parser.parseAlarmId(call.arguments).getOrElse { error ->
            return AgentToolExecution.Failure(
                AgentFailure.ToolExecutionFailed(error.message ?: "Invalid resume_alarm arguments."),
            )
        }

        return AgentToolExecution.Success(
            managementUseCase.resume(alarmId).toToolResult(
                toolCallId = call.id,
                toolName = spec.name,
                requestedAlarmId = alarmId,
                successStatus = "resumed",
            ),
        )
    }

    companion object {
        const val Name = "resume_alarm"
    }
}

class AlarmManagementArgumentsParser {
    private val json = Json {
        ignoreUnknownKeys = false
    }

    data class PauseArguments(
        val alarmId: Long,
        val mode: PauseMode,
    )

    fun parsePause(arguments: String): Result<PauseArguments> = runCatching {
        val root = parseObject(arguments)
        root.requireOnlyKeys(setOf("alarmId", "mode"))
        val mode = when (val modeText = root.requiredString("mode")) {
            "next_occurrence" -> PauseMode.NEXT_OCCURRENCE
            "indefinite" -> PauseMode.INDEFINITE
            else -> throw invalid("pause_alarm mode must be one of next_occurrence, indefinite.")
        }
        PauseArguments(
            alarmId = root.requiredLong("alarmId"),
            mode = mode,
        )
    }

    fun parseAlarmId(arguments: String): Result<Long> = runCatching {
        val root = parseObject(arguments)
        root.requireOnlyKeys(setOf("alarmId"))
        root.requiredLong("alarmId")
    }

    private fun parseObject(arguments: String): JsonObject = try {
        json.parseToJsonElement(arguments).jsonObject
    } catch (exception: IllegalArgumentException) {
        throw invalid("Invalid JSON", exception)
    } catch (exception: SerializationException) {
        throw invalid("Invalid JSON", exception)
    }

    private fun JsonObject.requireOnlyKeys(allowedKeys: Set<String>) {
        val unexpected = keys.firstOrNull { it !in allowedKeys } ?: return
        throw invalid("unexpected key: $unexpected")
    }

    private fun JsonObject.requiredString(name: String): String {
        val primitive = this[name] as? JsonPrimitive ?: throw invalid("$name is required")
        if (!primitive.isString) {
            throw invalid("$name must be a string")
        }
        return primitive.contentOrNull?.takeIf { it.isNotBlank() } ?: throw invalid("$name must be non-empty")
    }

    private fun JsonObject.requiredLong(name: String): Long {
        val primitive = this[name] as? JsonPrimitive ?: throw invalid("$name is required")
        if (primitive.isString) {
            throw invalid("$name must be an integer")
        }
        return primitive.longOrNull ?: throw invalid("$name must be an integer")
    }

    private fun invalid(message: String, cause: Throwable? = null): IllegalArgumentException =
        IllegalArgumentException(message, cause)
}

enum class PauseMode {
    NEXT_OCCURRENCE,
    INDEFINITE,
}

private fun AlarmManagementResult.toToolResult(
    toolCallId: String,
    toolName: String,
    requestedAlarmId: Long,
    successStatus: String,
): AgentToolResult {
    val committed = this is AlarmManagementResult.Updated
    return AgentToolResult(
        toolCallId = toolCallId,
        toolName = toolName,
        content = buildJsonObject {
            when (this@toToolResult) {
                is AlarmManagementResult.Updated -> {
                    put("status", successStatus)
                    put("alarmId", alarm.id)
                    put("title", alarm.title)
                    put("pauseState", alarm.pauseMode.toToolValue())
                }

                AlarmManagementResult.MissingAlarm -> {
                    put("status", "missing_alarm")
                    put("alarmId", requestedAlarmId)
                }

                is AlarmManagementResult.InvalidState -> {
                    put("status", "invalid_state")
                    put("alarmId", requestedAlarmId)
                    put("reason", reason)
                }

                is AlarmManagementResult.MissingSchedulingPermission -> {
                    put("status", "missing_scheduling_permission")
                    put("alarmId", requestedAlarmId)
                    put("permission", permission)
                }

                is AlarmManagementResult.SchedulerFailed -> {
                    put("status", "scheduler_failed")
                    put("alarmId", requestedAlarmId)
                    put("reason", reason)
                }

                is AlarmManagementResult.StaleOrMismatchedTrigger -> {
                    put("status", "stale_or_mismatched_state")
                    put("alarmId", alarmId)
                }
            }
        },
        committed = committed,
    )
}

private fun RepeatRule.toSummary(): String = when (this) {
    is RepeatRule.Once -> "once:$date"
    RepeatRule.Daily -> "daily"
    RepeatRule.Weekdays -> "weekdays"
    is RepeatRule.CustomWeekdays -> "custom_weekdays:${days.toSortedSummary()}"
    is RepeatRule.WeeklyInterval -> "weekly_interval:${intervalWeeks}:${days.toSortedSummary()}"
}

private fun Set<DayOfWeek>.toSortedSummary(): String =
    sortedBy { it.value }.joinToString(",") { it.value.toString() }

private fun AlarmPauseMode.toToolValue(): String = when (this) {
    AlarmPauseMode.NONE -> "none"
    AlarmPauseMode.NEXT_OCCURRENCE -> "next_occurrence"
    AlarmPauseMode.INDEFINITE -> "indefinite"
}

private fun emptyObjectParameters(): JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    putJsonObject("properties") {}
}

private fun alarmIdParameters(): JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    putJsonObject("properties") {
        putJsonObject("alarmId") {
            put("type", "integer")
            put("description", "The exact alarm id from list_alarms or prior user context.")
        }
    }
    putJsonArray("required") {
        addString("alarmId")
    }
}

private fun pauseParameters(): JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    putJsonObject("properties") {
        putJsonObject("alarmId") {
            put("type", "integer")
            put("description", "The exact alarm id from list_alarms or prior user context.")
        }
        putJsonObject("mode") {
            put("type", "string")
            put("enum", buildJsonArray {
                addString("next_occurrence")
                addString("indefinite")
            })
        }
    }
    putJsonArray("required") {
        addString("alarmId")
        addString("mode")
    }
}

private fun JsonArrayBuilder.addString(value: String) {
    add(JsonPrimitive(value))
}
