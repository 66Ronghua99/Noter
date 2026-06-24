package com.cory.noter.agent.tools.alarm

import com.cory.noter.agent.AgentFailure
import com.cory.noter.agent.AgentTool
import com.cory.noter.agent.AgentToolCall
import com.cory.noter.agent.AgentToolExecution
import com.cory.noter.agent.AgentToolResult
import com.cory.noter.agent.AgentToolRisk
import com.cory.noter.agent.AgentToolSpec
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.ScheduleResult
import com.cory.noter.data.alarm.AlarmDraft
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.AlarmValidation
import java.time.Clock
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class CreateAlarmToolContext(
    val userRequest: String,
    val ringtoneUri: String,
)

class CreateAlarmTool(
    private val context: CreateAlarmToolContext,
    private val alarmRepository: AlarmRepository,
    private val schedulingUseCase: AlarmSchedulingUseCase,
    private val parser: CreateAlarmArgumentsParser = CreateAlarmArgumentsParser(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : AgentTool {
    override val spec: AgentToolSpec = AgentToolSpec(
        name = "create_alarm",
        description = "Create and schedule one local Android alarm from validated alarm fields.",
        parameters = createAlarmParameters(),
        risk = AgentToolRisk.WRITE,
    )

    override suspend fun execute(call: AgentToolCall): AgentToolExecution {
        val parsedDraft = parser.parse(call.arguments).getOrElse { error ->
            return when (error) {
                is CreateAlarmArgumentsParser.ClarificationRequiredException -> {
                    AgentToolExecution.Failure(
                        AgentFailure.ClarificationRequired(error.reason),
                    )
                }

                else -> AgentToolExecution.Failure(
                    AgentFailure.ToolExecutionFailed(error.message ?: "Invalid create_alarm arguments."),
                )
            }
        }

        val validationErrors = AlarmValidation.validateDraft(
            title = parsedDraft.title,
            hour = parsedDraft.hour,
            minute = parsedDraft.minute,
            repeatRule = parsedDraft.repeatRule,
            now = clock.instant(),
            zoneId = clock.zone,
        )
        if (validationErrors.isNotEmpty()) {
            return AgentToolExecution.Failure(
                AgentFailure.ToolExecutionFailed(
                    "Alarm validation failed: ${validationErrors.joinToString(", ")}",
                ),
            )
        }

        val createdAlarm = runCatching {
            alarmRepository.create(
                AlarmDraft(
                    title = parsedDraft.title,
                    hour = parsedDraft.hour,
                    minute = parsedDraft.minute,
                    repeatRule = parsedDraft.repeatRule,
                    enabled = true,
                    ringtoneUri = context.ringtoneUri,
                    source = AlarmSource.AI,
                    aiOriginalText = context.userRequest,
                ),
            )
        }.getOrElse { error ->
            return AgentToolExecution.Failure(
                AgentFailure.CreateFailed(error.message ?: "Alarm creation failed."),
            )
        }

        return when (val scheduleResult = schedulingUseCase.syncSchedule(createdAlarm)) {
            ScheduleResult.Scheduled -> AgentToolExecution.Success(
                AgentToolResult(
                    toolCallId = call.id,
                    toolName = spec.name,
                    content = createdAlarmContent("created", createdAlarm),
                    committed = true,
                ),
            )

            ScheduleResult.Cancelled -> AgentToolExecution.Failure(
                failure = AgentFailure.ToolExecutionFailed(
                    "Alarm ${createdAlarm.id} scheduling returned Cancelled unexpectedly.",
                ),
                committedResult = AgentToolResult(
                    toolCallId = call.id,
                    toolName = spec.name,
                    content = createdAlarmContent("schedule_failed", createdAlarm) {
                        put("reason", "Alarm ${createdAlarm.id} scheduling returned Cancelled unexpectedly.")
                    },
                    committed = true,
                ),
            )

            is ScheduleResult.MissingPermission -> AgentToolExecution.Failure(
                failure = AgentFailure.ToolExecutionFailed(
                    "Missing scheduling permission: ${scheduleResult.permission}",
                ),
                committedResult = AgentToolResult(
                    toolCallId = call.id,
                    toolName = spec.name,
                    content = createdAlarmContent("missing_scheduling_permission", createdAlarm) {
                        put("permission", scheduleResult.permission)
                    },
                    committed = true,
                ),
            )

            is ScheduleResult.Failed -> AgentToolExecution.Failure(
                failure = AgentFailure.ToolExecutionFailed(scheduleResult.reason),
                committedResult = AgentToolResult(
                    toolCallId = call.id,
                    toolName = spec.name,
                    content = createdAlarmContent("schedule_failed", createdAlarm) {
                        put("reason", scheduleResult.reason)
                    },
                    committed = true,
                ),
            )
        }
    }

    private fun createdAlarmContent(
        status: String,
        alarm: Alarm,
        extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {},
    ): JsonObject = buildJsonObject {
        put("status", status)
        put("alarmId", alarm.id)
        put("title", alarm.title)
        extra()
    }

    private fun createAlarmParameters(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put("description", "Short alarm title, such as Take medicine.")
            }
            putJsonObject("hour") {
                put("type", "integer")
                put("minimum", 0)
                put("maximum", 23)
                put("description", "Local 24-hour clock hour.")
            }
            putJsonObject("minute") {
                put("type", "integer")
                put("minimum", 0)
                put("maximum", 59)
                put("description", "Local clock minute.")
            }
            putJsonObject("repeatRule") {
                put("type", "object")
                put("additionalProperties", false)
                putJsonObject("properties") {
                    putJsonObject("type") {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            addString("once")
                            addString("daily")
                            addString("weekdays")
                            addString("custom_weekdays")
                            addString("weekly_interval")
                        })
                    }
                    putJsonObject("daysOfWeek") {
                        put("type", "array")
                        put(
                            "description",
                            "ISO weekdays for custom_weekdays and weekly_interval. Monday is 1 and Sunday is 7.",
                        )
                        putJsonObject("items") {
                            put("type", "integer")
                            put("minimum", 1)
                            put("maximum", 7)
                        }
                    }
                    putJsonObject("startDate") {
                        put("type", buildJsonArray {
                            addString("string")
                            addString("null")
                        })
                        put("description", "ISO local date yyyy-MM-dd required for weekly_interval; use null otherwise.")
                    }
                    putJsonObject("endDate") {
                        put("type", buildJsonArray {
                            addString("string")
                            addString("null")
                        })
                        put(
                            "description",
                            "ISO local date yyyy-MM-dd for weekly_interval when the user gives an end date; use null so the app defaults to one year after startDate when omitted.",
                        )
                    }
                    putJsonObject("intervalWeeks") {
                        put("type", buildJsonArray {
                            addString("integer")
                            addString("null")
                        })
                        put("minimum", 1)
                        put("description", "Positive week interval required for weekly_interval; use null otherwise.")
                    }
                }
                putJsonArray("required") {
                    addString("type")
                    addString("daysOfWeek")
                }
            }
            putJsonObject("date") {
                put("type", buildJsonArray {
                    addString("string")
                    addString("null")
                })
                put("description", "ISO local date yyyy-MM-dd for once alarms. Use null for repeating alarms.")
            }
            putJsonObject("confidence") {
                put("type", "number")
                put("minimum", 0.0)
                put("maximum", 1.0)
            }
            putJsonObject("needsClarification") {
                put("type", "boolean")
            }
            putJsonObject("clarificationReason") {
                put("type", "string")
                put("description", "Non-empty only when needsClarification is true.")
            }
        }
        putJsonArray("required") {
            addString("title")
            addString("hour")
            addString("minute")
            addString("repeatRule")
            addString("date")
            addString("confidence")
            addString("needsClarification")
            addString("clarificationReason")
        }
    }

    private fun JsonArrayBuilder.addString(value: String) {
        add(JsonPrimitive(value))
    }
}
