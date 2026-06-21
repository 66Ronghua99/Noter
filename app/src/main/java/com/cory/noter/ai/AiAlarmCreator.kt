package com.cory.noter.ai

import com.cory.noter.agent.AgentFailure
import com.cory.noter.agent.AgentLoopRunner
import com.cory.noter.agent.AgentMessage
import com.cory.noter.agent.AgentMessageRole
import com.cory.noter.agent.AgentRunRequest
import com.cory.noter.agent.AgentRunResult
import com.cory.noter.agent.AgentToolChoice
import com.cory.noter.agent.AgentToolRegistry
import com.cory.noter.agent.AgentToolResult
import com.cory.noter.agent.tools.alarm.CreateAlarmTool
import com.cory.noter.agent.tools.alarm.CreateAlarmToolContext
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.data.settings.SettingsRepository
import com.cory.noter.domain.alarm.Alarm
import java.time.Clock
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

sealed interface AiCreateResult {
    data object MissingApiKey : AiCreateResult
    data object MissingModel : AiCreateResult
    data class NetworkFailure(val reason: String) : AiCreateResult
    data class RateLimited(val reason: String) : AiCreateResult
    data class RemoteFailure(val code: Int, val reason: String) : AiCreateResult
    data class InvalidResponse(val reason: String) : AiCreateResult
    data class ClarificationRequired(val reason: String) : AiCreateResult
    data class CreateFailed(val reason: String) : AiCreateResult
    data class MissingSchedulingPermission(val alarm: Alarm, val permission: String) : AiCreateResult
    data class ScheduleFailed(val alarm: Alarm, val reason: String) : AiCreateResult
    data class Created(val alarm: Alarm) : AiCreateResult
}

class AiAlarmCreator(
    private val settingsRepository: SettingsRepository,
    private val agentLoopRunner: AgentLoopRunner,
    private val alarmRepository: AlarmRepository,
    private val schedulingUseCase: AlarmSchedulingUseCase,
    private val promptBuilder: AiAlarmPromptBuilder = AiAlarmPromptBuilder(),
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun createFromText(userRequest: String): AiCreateResult {
        val settings = settingsRepository.settings.first()
        val apiKey = settings.openRouterApiKey.trim()
        if (apiKey.isEmpty()) {
            return AiCreateResult.MissingApiKey
        }

        val modelId = settings.selectedModelId.trim()
        if (modelId.isEmpty() || modelId !in OpenRouterModel.builtInIds) {
            return AiCreateResult.MissingModel
        }

        val toolRegistry = AgentToolRegistry(
            listOf(
                CreateAlarmTool(
                    context = CreateAlarmToolContext(
                        userRequest = userRequest,
                        ringtoneUri = settings.defaultRingtoneUri,
                    ),
                    alarmRepository = alarmRepository,
                    schedulingUseCase = schedulingUseCase,
                    clock = clock,
                ),
            ),
        )

        val result = agentLoopRunner.run(
            AgentRunRequest(
                apiKey = apiKey,
                modelId = modelId,
                initialMessages = listOf(
                    AgentMessage(
                        AgentMessageRole.SYSTEM,
                        promptBuilder.build(userRequest, ZonedDateTime.now(clock)),
                    ),
                ),
                toolRegistry = toolRegistry,
                toolChoice = AgentToolChoice.Required("create_alarm"),
            ),
        )

        return result.toAiCreateResult()
    }

    private suspend fun AgentRunResult.toAiCreateResult(): AiCreateResult = when (this) {
        is AgentRunResult.Completed -> toolResults.lastOrNull()?.toAiCreateResult()
            ?: AiCreateResult.InvalidResponse("Agent completed without a tool result.")

        is AgentRunResult.CompletedWithFinalizationFailure -> committedResults.last().toAiCreateResult()

        is AgentRunResult.Failed -> failure.toAiCreateResult()
    }

    private suspend fun AgentToolResult.toAiCreateResult(): AiCreateResult {
        val status = content.requiredString("status")
            ?: return AiCreateResult.InvalidResponse("Agent tool result did not include a valid status.")
        val alarmId = content.requiredLong("alarmId")
            ?: return AiCreateResult.InvalidResponse("Agent tool result referenced a missing alarm.")
        val alarm = alarmRepository.get(alarmId)
            ?: return AiCreateResult.InvalidResponse("Agent tool result referenced a missing alarm.")

        return when (status) {
            "created" -> AiCreateResult.Created(alarm)

            "missing_scheduling_permission" -> {
                val permission = content.requiredString("permission")
                    ?: return AiCreateResult.InvalidResponse(
                        "Agent tool result did not include the missing scheduling permission.",
                    )
                AiCreateResult.MissingSchedulingPermission(alarm, permission)
            }

            "schedule_failed" -> {
                val reason = content.requiredString("reason")
                    ?: return AiCreateResult.InvalidResponse(
                        "Agent tool result did not include a scheduling failure reason.",
                    )
                AiCreateResult.ScheduleFailed(alarm, reason)
            }

            else -> AiCreateResult.InvalidResponse("Unsupported agent tool result status: $status")
        }
    }

    private fun AgentFailure.toAiCreateResult(): AiCreateResult = when (this) {
        is AgentFailure.ModelFailure -> AiCreateResult.InvalidResponse(reason)
        is AgentFailure.NetworkFailure -> AiCreateResult.NetworkFailure(reason)
        is AgentFailure.RateLimited -> AiCreateResult.RateLimited(reason)
        is AgentFailure.RemoteFailure -> AiCreateResult.RemoteFailure(code, reason)
        is AgentFailure.ToolExecutionFailed -> AiCreateResult.InvalidResponse(reason)
        is AgentFailure.ClarificationRequired -> AiCreateResult.ClarificationRequired(reason)
        is AgentFailure.CreateFailed -> AiCreateResult.CreateFailed(reason)
        is AgentFailure.MissingToolCall -> AiCreateResult.InvalidResponse(reason)
        is AgentFailure.ToolNotRegistered -> AiCreateResult.InvalidResponse(
            "Tool is not registered: $toolName",
        )
        is AgentFailure.ToolLimitExceeded -> AiCreateResult.InvalidResponse(reason)
        is AgentFailure.ModelTurnLimitExceeded -> AiCreateResult.InvalidResponse(reason)
    }

    private fun Map<String, JsonElement>.requiredString(key: String): String? =
        (get(key) as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.takeIf { it.isNotBlank() }

    private fun Map<String, JsonElement>.requiredLong(key: String): Long? =
        (get(key) as? JsonPrimitive)?.longOrNull
}
