package com.cory.noter.ai

import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.ScheduleResult
import com.cory.noter.data.alarm.AlarmDraft
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.data.settings.SettingsRepository
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.AlarmValidation
import java.time.Clock
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

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
    private val openRouterClient: OpenRouterGateway,
    private val alarmRepository: AlarmRepository,
    private val schedulingUseCase: AlarmSchedulingUseCase,
    private val promptBuilder: AiAlarmPromptBuilder = AiAlarmPromptBuilder(),
    private val responseParser: AiAlarmResponseParser = AiAlarmResponseParser(),
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

        val prompt = promptBuilder.build(
            userRequest = userRequest,
            now = ZonedDateTime.now(clock),
        )

        return when (
            val remoteResult = openRouterClient.createChatCompletion(
                apiKey = apiKey,
                modelId = modelId,
                prompt = prompt,
            )
        ) {
            is OpenRouterResult.Success -> createAlarmFromResponse(
                responseText = remoteResult.responseText,
                userRequest = userRequest,
                ringtoneUri = settings.defaultRingtoneUri,
            )
            is OpenRouterResult.NetworkFailure -> AiCreateResult.NetworkFailure(remoteResult.reason)
            is OpenRouterResult.RateLimited -> AiCreateResult.RateLimited(remoteResult.reason)
            is OpenRouterResult.RemoteFailure -> AiCreateResult.RemoteFailure(
                remoteResult.code,
                remoteResult.reason,
            )
            is OpenRouterResult.InvalidResponse -> AiCreateResult.InvalidResponse(remoteResult.reason)
        }
    }

    private suspend fun createAlarmFromResponse(
        responseText: String,
        userRequest: String,
        ringtoneUri: String,
    ): AiCreateResult {
        val parsedDraft = responseParser.parse(responseText).getOrElse { error ->
            return when (error) {
                is AiAlarmResponseParser.ClarificationRequiredException -> {
                    AiCreateResult.ClarificationRequired(error.reason)
                }

                else -> AiCreateResult.InvalidResponse(
                    error.message ?: "OpenRouter returned an invalid alarm response.",
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
            return AiCreateResult.InvalidResponse(
                "Alarm validation failed: ${validationErrors.joinToString(", ")}",
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
                    ringtoneUri = ringtoneUri,
                    source = AlarmSource.AI,
                    aiOriginalText = userRequest,
                ),
            )
        }.getOrElse { error ->
            return AiCreateResult.CreateFailed(
                error.message ?: "Alarm creation failed.",
            )
        }

        return when (val scheduleResult = schedulingUseCase.syncSchedule(createdAlarm)) {
            ScheduleResult.Scheduled -> AiCreateResult.Created(createdAlarm)
            ScheduleResult.Cancelled -> AiCreateResult.ScheduleFailed(
                createdAlarm,
                "Alarm ${createdAlarm.id} scheduling returned Cancelled unexpectedly.",
            )
            is ScheduleResult.MissingPermission -> AiCreateResult.MissingSchedulingPermission(
                createdAlarm,
                scheduleResult.permission,
            )
            is ScheduleResult.Failed -> AiCreateResult.ScheduleFailed(
                createdAlarm,
                scheduleResult.reason,
            )
        }
    }
}
