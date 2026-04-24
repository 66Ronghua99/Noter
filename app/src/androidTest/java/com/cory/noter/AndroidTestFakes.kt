package com.cory.noter

import com.cory.noter.ai.OpenRouterGateway
import com.cory.noter.ai.OpenRouterResult
import com.cory.noter.alarm.AlarmScheduler
import com.cory.noter.alarm.ScheduleResult
import com.cory.noter.data.alarm.AlarmDraft
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.data.settings.SettingsRepository
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.NextTriggerCalculator
import com.cory.noter.domain.settings.AppSettings
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class AndroidTestAlarmRepository(
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val nextTriggerCalculator: NextTriggerCalculator = NextTriggerCalculator(),
) : AlarmRepository {
    private val state = MutableStateFlow<List<Alarm>>(emptyList())
    private var nextId = 1L

    override val alarms: Flow<List<Alarm>> = state

    override suspend fun get(id: Long): Alarm? = state.value.firstOrNull { it.id == id }

    override suspend fun create(draft: AlarmDraft): Alarm {
        val nowMillis = clock.millis()
        val alarm = Alarm(
            id = nextId++,
            title = draft.title,
            hour = draft.hour,
            minute = draft.minute,
            repeatRule = draft.repeatRule,
            enabled = draft.enabled,
            ringtoneUri = draft.ringtoneUri,
            source = draft.source,
            aiOriginalText = draft.aiOriginalText,
            nextTriggerAtMillis = computeNextTriggerAtMillis(
                hour = draft.hour,
                minute = draft.minute,
                enabled = draft.enabled,
                repeatRule = draft.repeatRule,
            ),
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        )
        state.update { it + alarm }
        return alarm
    }

    override suspend fun update(alarm: Alarm): Alarm {
        val updated = alarm.copy(
            nextTriggerAtMillis = computeNextTriggerAtMillis(
                hour = alarm.hour,
                minute = alarm.minute,
                enabled = alarm.enabled,
                repeatRule = alarm.repeatRule,
            ),
            updatedAtMillis = clock.millis(),
        )
        state.update { alarms -> alarms.map { existing -> if (existing.id == alarm.id) updated else existing } }
        return updated
    }

    override suspend fun enable(id: Long): Alarm? = get(id)?.let { update(it.copy(enabled = true)) }

    override suspend fun disable(id: Long): Alarm? = get(id)?.let { update(it.copy(enabled = false)) }

    override suspend fun delete(id: Long) {
        state.update { alarms -> alarms.filterNot { it.id == id } }
    }

    suspend fun seed(alarm: Alarm) {
        nextId = maxOf(nextId, alarm.id + 1)
        state.update { it + alarm }
    }

    private fun computeNextTriggerAtMillis(
        hour: Int,
        minute: Int,
        enabled: Boolean,
        repeatRule: com.cory.noter.domain.alarm.RepeatRule,
    ): Long? {
        if (!enabled) {
            return null
        }
        return nextTriggerCalculator.nextTrigger(
            hour = hour,
            minute = minute,
            repeatRule = repeatRule,
            now = clock.instant(),
            zoneId = zoneId,
        )?.toEpochMilli()
    }
}

class AndroidTestSettingsRepository(
    initial: AppSettings = AppSettings(
        openRouterApiKey = "",
        selectedModelId = com.cory.noter.ai.OpenRouterModel.DefaultId,
        defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
    ),
) : SettingsRepository {
    private val mutableSettings = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = mutableSettings

    override suspend fun setOpenRouterApiKey(apiKey: String): Result<Unit> = runCatching {
        mutableSettings.update { it.copy(openRouterApiKey = apiKey) }
    }

    override suspend fun setSelectedModel(modelId: String): Result<Unit> = runCatching {
        mutableSettings.update { it.copy(selectedModelId = modelId) }
    }

    override suspend fun setDefaultRingtoneUri(ringtoneUri: String): Result<Unit> = runCatching {
        mutableSettings.update { it.copy(defaultRingtoneUri = ringtoneUri) }
    }
}

class AndroidTestAlarmScheduler : AlarmScheduler {
    val scheduledIds = mutableListOf<Long>()
    val canceledIds = mutableListOf<Long>()

    override fun schedule(alarm: Alarm): ScheduleResult {
        scheduledIds += alarm.id
        return ScheduleResult.Scheduled
    }

    override fun cancel(alarmId: Long): ScheduleResult {
        canceledIds += alarmId
        return ScheduleResult.Cancelled
    }
}

class AndroidTestOpenRouterGateway(
    var nextResult: OpenRouterResult = OpenRouterResult.Success("{}"),
) : OpenRouterGateway {
    override suspend fun createChatCompletion(
        apiKey: String,
        modelId: String,
        prompt: String,
    ): OpenRouterResult = nextResult
}
