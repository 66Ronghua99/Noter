package com.cory.noter.data.alarm

import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmValidation
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.NextTriggerCalculator
import com.cory.noter.domain.alarm.RepeatRule
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAlarmRepository(
    private val alarmDao: AlarmDao,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val nextTriggerCalculator: NextTriggerCalculator = NextTriggerCalculator(),
    private val repeatRuleCodec: RepeatRuleCodec = RepeatRuleCodec(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : AlarmRepository {
    override val alarms: Flow<List<Alarm>> = alarmDao.observeAll().map { entities ->
        entities.map(::entityToDomain)
    }

    override suspend fun get(id: Long): Alarm? = alarmDao.getById(id)?.let(::entityToDomain)

    override suspend fun create(draft: AlarmDraft): Alarm {
        val nowMillis = clock.millis()
        val zoneId = zoneIdProvider()
        validateDraftOrThrow(
            title = draft.title,
            hour = draft.hour,
            minute = draft.minute,
            repeatRule = draft.repeatRule,
            enabled = draft.enabled,
            zoneId = zoneId,
        )
        val encodedRepeatRule = repeatRuleCodec.encode(draft.repeatRule)
        val alarmId = alarmDao.insert(
            AlarmEntity(
                id = 0,
                title = draft.title,
                hour = draft.hour,
                minute = draft.minute,
                repeatType = encodedRepeatRule.repeatType,
                daysOfWeekCsv = encodedRepeatRule.daysOfWeekCsv,
                onceDate = encodedRepeatRule.onceDate,
                startDate = encodedRepeatRule.startDate,
                endDate = encodedRepeatRule.endDate,
                intervalWeeks = encodedRepeatRule.intervalWeeks,
                enabled = draft.enabled,
                ringtoneUri = draft.ringtoneUri,
                source = draft.source.toStorageValue(),
                aiOriginalText = draft.aiOriginalText,
                nextTriggerAtMillis = computeNextTriggerAtMillis(
                    hour = draft.hour,
                    minute = draft.minute,
                    repeatRule = draft.repeatRule,
                    enabled = draft.enabled,
                    zoneId = zoneId,
                ),
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis,
            ),
        )

        return requireNotNull(get(alarmId)) { "Created alarm $alarmId could not be loaded." }
    }

    override suspend fun update(alarm: Alarm): Alarm {
        val existing = requireNotNull(alarmDao.getById(alarm.id)) {
            "Alarm ${alarm.id} does not exist."
        }
        val nowMillis = clock.millis()
        val zoneId = zoneIdProvider()
        validateDraftOrThrow(
            title = alarm.title,
            hour = alarm.hour,
            minute = alarm.minute,
            repeatRule = alarm.repeatRule,
            enabled = alarm.enabled,
            zoneId = zoneId,
        )
        val encodedRepeatRule = repeatRuleCodec.encode(alarm.repeatRule)
        alarmDao.update(
            AlarmEntity(
                id = alarm.id,
                title = alarm.title,
                hour = alarm.hour,
                minute = alarm.minute,
                repeatType = encodedRepeatRule.repeatType,
                daysOfWeekCsv = encodedRepeatRule.daysOfWeekCsv,
                onceDate = encodedRepeatRule.onceDate,
                startDate = encodedRepeatRule.startDate,
                endDate = encodedRepeatRule.endDate,
                intervalWeeks = encodedRepeatRule.intervalWeeks,
                enabled = alarm.enabled,
                ringtoneUri = alarm.ringtoneUri,
                source = alarm.source.toStorageValue(),
                aiOriginalText = alarm.aiOriginalText,
                nextTriggerAtMillis = computeNextTriggerAtMillis(
                    hour = alarm.hour,
                    minute = alarm.minute,
                    repeatRule = alarm.repeatRule,
                    enabled = alarm.enabled,
                    zoneId = zoneId,
                ),
                createdAtMillis = existing.createdAtMillis,
                updatedAtMillis = nowMillis,
            ),
        )

        return requireNotNull(get(alarm.id)) { "Updated alarm ${alarm.id} could not be loaded." }
    }

    override suspend fun enable(id: Long): Alarm? {
        val alarm = get(id) ?: return null
        return update(alarm.copy(enabled = true))
    }

    override suspend fun disable(id: Long): Alarm? {
        val alarm = get(id) ?: return null
        return update(alarm.copy(enabled = false))
    }

    override suspend fun delete(id: Long) {
        alarmDao.deleteById(id)
    }

    private fun entityToDomain(entity: AlarmEntity): Alarm = Alarm(
        id = entity.id,
        title = entity.title,
        hour = entity.hour,
        minute = entity.minute,
        repeatRule = repeatRuleCodec.decode(
            repeatType = entity.repeatType,
            daysOfWeekCsv = entity.daysOfWeekCsv,
            onceDate = entity.onceDate,
            startDate = entity.startDate,
            endDate = entity.endDate,
            intervalWeeks = entity.intervalWeeks,
        ),
        enabled = entity.enabled,
        ringtoneUri = entity.ringtoneUri,
        source = entity.source.toAlarmSource(),
        aiOriginalText = entity.aiOriginalText,
        nextTriggerAtMillis = entity.nextTriggerAtMillis,
        createdAtMillis = entity.createdAtMillis,
        updatedAtMillis = entity.updatedAtMillis,
    )

    private fun computeNextTriggerAtMillis(
        hour: Int,
        minute: Int,
        repeatRule: RepeatRule,
        enabled: Boolean,
        zoneId: ZoneId,
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

    private fun validateDraftOrThrow(
        title: String,
        hour: Int,
        minute: Int,
        repeatRule: RepeatRule,
        enabled: Boolean,
        zoneId: ZoneId,
    ) {
        val errors = AlarmValidation.validateDraft(
            title = title,
            hour = hour,
            minute = minute,
            repeatRule = repeatRule,
            now = clock.instant(),
            zoneId = zoneId,
            nextTriggerCalculator = nextTriggerCalculator,
        ).filterNot { error ->
            !enabled && error in setOf(
                AlarmValidation.Error.EXPIRED_ONE_TIME_ALARM,
                AlarmValidation.Error.EXPIRED_INTERVAL_ALARM,
            )
        }

        require(errors.isEmpty()) {
            "Alarm validation failed: ${errors.joinToString(", ")}"
        }
    }

    private fun AlarmSource.toStorageValue(): String = when (this) {
        AlarmSource.MANUAL -> "manual"
        AlarmSource.AI -> "ai"
    }

    private fun String.toAlarmSource(): AlarmSource = when (this) {
        "manual" -> AlarmSource.MANUAL
        "ai" -> AlarmSource.AI
        else -> error("Unsupported alarm source: $this")
    }
}
