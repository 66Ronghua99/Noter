package com.cory.noter.calendar

import com.cory.noter.data.alarm.AlarmCalendarEventMapping
import com.cory.noter.data.alarm.AlarmCalendarEventRepository
import com.cory.noter.data.settings.FakeSettingsRepository
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmPauseMode
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.RepeatRule
import com.cory.noter.domain.settings.AppSettings
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CalendarSyncUseCaseTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-04-23T01:00:00Z"), zoneId)

    @Test
    fun `disabled sync is skipped without provider insert or mapping write`() = runTest {
        val writer = RecordingCalendarEventWriter()
        val mappings = RecordingCalendarEventRepository()
        val useCase = useCase(writer = writer, mappingRepository = mappings)

        val result = useCase.sync(
            alarm = onceAlarm(),
            request = CalendarSyncRequest(enabled = false, reason = "none", durationMinutes = 30),
        )

        assertThat(result.status).isEqualTo(CalendarSyncStatus.SKIPPED)
        assertThat(writer.insertedEvents).isEmpty()
        assertThat(mappings.insertedMappings).isEmpty()
    }

    @Test
    fun `missing calendar permission returns missing permission without provider insert`() = runTest {
        val writer = RecordingCalendarEventWriter()
        val useCase = useCase(
            calendarSource = FakeCalendarSource(CalendarSourceResult.MissingPermission),
            writer = writer,
        )

        val result = useCase.sync(onceAlarm(), enabledRequest())

        assertThat(result.status).isEqualTo(CalendarSyncStatus.MISSING_CALENDAR_PERMISSION)
        assertThat(writer.insertedEvents).isEmpty()
    }

    @Test
    fun `missing default calendar returns missing default without provider insert`() = runTest {
        val writer = RecordingCalendarEventWriter()
        val useCase = useCase(
            settingsRepository = FakeSettingsRepository(),
            writer = writer,
        )

        val result = useCase.sync(onceAlarm(), enabledRequest())

        assertThat(result.status).isEqualTo(CalendarSyncStatus.MISSING_DEFAULT_CALENDAR)
        assertThat(writer.insertedEvents).isEmpty()
    }

    @Test
    fun `stored calendar that is missing or read only returns calendar not writable`() = runTest {
        val missingCalendarResult = useCase(
            calendarSource = FakeCalendarSource(CalendarSourceResult.Available(emptyList())),
        ).sync(onceAlarm(), enabledRequest())
        val readOnlyCalendarResult = useCase(
            calendarSource = FakeCalendarSource(
                CalendarSourceResult.Available(
                    listOf(
                        DeviceCalendar(
                            id = 42L,
                            displayName = "Read only",
                            accountName = "readonly@example.com",
                            accountType = "com.google",
                            writable = false,
                        ),
                    ),
                ),
            ),
        ).sync(onceAlarm(), enabledRequest())

        assertThat(missingCalendarResult.status).isEqualTo(CalendarSyncStatus.CALENDAR_NOT_WRITABLE)
        assertThat(readOnlyCalendarResult.status).isEqualTo(CalendarSyncStatus.CALENDAR_NOT_WRITABLE)
    }

    @Test
    fun `once alarm writes dtstart dtend title calendar id and timezone then persists mapping`() = runTest {
        val writer = RecordingCalendarEventWriter(nextEventId = 9001L)
        val mappings = RecordingCalendarEventRepository()
        val useCase = useCase(writer = writer, mappingRepository = mappings)

        val result = useCase.sync(onceAlarm(), enabledRequest(durationMinutes = 45))

        assertThat(result.status).isEqualTo(CalendarSyncStatus.SYNCED)
        assertThat(writer.insertedEvents.single()).isEqualTo(
            CalendarEventInsert(
                calendarId = 42L,
                title = "Doctor appointment",
                dtStartMillis = ZonedDateTime.of(2026, 4, 24, 9, 30, 0, 0, zoneId)
                    .toInstant()
                    .toEpochMilli(),
                dtEndMillis = ZonedDateTime.of(2026, 4, 24, 10, 15, 0, 0, zoneId)
                    .toInstant()
                    .toEpochMilli(),
                duration = null,
                rrule = null,
                eventTimeZone = "Asia/Shanghai",
            ),
        )
        assertThat(mappings.insertedMappings.single()).isEqualTo(
            AlarmCalendarEventMapping(
                alarmId = 7L,
                calendarId = 42L,
                eventId = 9001L,
                durationMinutes = 45,
                reason = "explicit_user_request",
                status = "synced",
                createdAtMillis = clock.millis(),
                updatedAtMillis = clock.millis(),
            ),
        )
    }

    @Test
    fun `repeating alarm writes dtstart duration rrule title calendar id and timezone`() = runTest {
        val writer = RecordingCalendarEventWriter(nextEventId = 9002L)
        val useCase = useCase(writer = writer)

        val result = useCase.sync(
            alarm = dailyAlarm(),
            request = enabledRequest(reason = "calendar_like_event", durationMinutes = 30),
        )

        assertThat(result.status).isEqualTo(CalendarSyncStatus.SYNCED)
        assertThat(writer.insertedEvents.single()).isEqualTo(
            CalendarEventInsert(
                calendarId = 42L,
                title = "Morning standup",
                dtStartMillis = ZonedDateTime.of(2026, 4, 24, 8, 0, 0, 0, zoneId)
                    .toInstant()
                    .toEpochMilli(),
                dtEndMillis = null,
                duration = "PT30M",
                rrule = "FREQ=DAILY",
                eventTimeZone = "Asia/Shanghai",
            ),
        )
    }

    @Test
    fun `provider insert failure returns calendar insert failed without mapping write`() = runTest {
        val writer = RecordingCalendarEventWriter(failure = IllegalStateException("provider down"))
        val mappings = RecordingCalendarEventRepository()
        val useCase = useCase(writer = writer, mappingRepository = mappings)

        val result = useCase.sync(onceAlarm(), enabledRequest())

        assertThat(result.status).isEqualTo(CalendarSyncStatus.CALENDAR_INSERT_FAILED)
        assertThat(mappings.insertedMappings).isEmpty()
    }

    @Test
    fun `mapping persistence failure returns mapping persist failed after provider insert`() = runTest {
        val writer = RecordingCalendarEventWriter(nextEventId = 9003L)
        val useCase = useCase(
            writer = writer,
            mappingRepository = RecordingCalendarEventRepository(
                insertFailure = IllegalStateException("database full"),
            ),
        )

        val result = useCase.sync(onceAlarm(), enabledRequest())

        assertThat(result.status).isEqualTo(CalendarSyncStatus.MAPPING_PERSIST_FAILED)
        assertThat((result as CalendarSyncResult.MappingPersistFailed).eventId).isEqualTo(9003L)
        assertThat(writer.insertedEvents).hasSize(1)
    }

    private fun useCase(
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(
            initialSettings = AppSettings(
                openRouterApiKey = "",
                selectedModelId = "openai/gpt-4.1-mini",
                selectedAsrModelId = "openai/gpt-4o-mini-transcribe",
                defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
                defaultCalendarId = 42L,
            ),
        ),
        calendarSource: CalendarSource = FakeCalendarSource(
            CalendarSourceResult.Available(
                listOf(
                    DeviceCalendar(
                        id = 42L,
                        displayName = "Personal",
                        accountName = "me@example.com",
                        accountType = "com.microsoft.exchange",
                        writable = true,
                    ),
                ),
            ),
        ),
        writer: RecordingCalendarEventWriter = RecordingCalendarEventWriter(),
        mappingRepository: AlarmCalendarEventRepository = RecordingCalendarEventRepository(),
    ): CalendarSyncUseCase = CalendarSyncUseCase(
        settingsRepository = settingsRepository,
        calendarSource = calendarSource,
        eventWriter = writer,
        mappingRepository = mappingRepository,
        clock = clock,
        zoneIdProvider = { zoneId },
    )

    private fun enabledRequest(
        reason: String = "explicit_user_request",
        durationMinutes: Int = 30,
    ): CalendarSyncRequest = CalendarSyncRequest(
        enabled = true,
        reason = reason,
        durationMinutes = durationMinutes,
    )

    private fun onceAlarm(): Alarm = alarm(
        id = 7L,
        title = "Doctor appointment",
        repeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 24)),
        nextTriggerAtMillis = ZonedDateTime.of(2026, 4, 24, 9, 30, 0, 0, zoneId)
            .toInstant()
            .toEpochMilli(),
        hour = 9,
        minute = 30,
    )

    private fun dailyAlarm(): Alarm = alarm(
        id = 8L,
        title = "Morning standup",
        repeatRule = RepeatRule.Daily,
        nextTriggerAtMillis = ZonedDateTime.of(2026, 4, 24, 8, 0, 0, 0, zoneId)
            .toInstant()
            .toEpochMilli(),
        hour = 8,
        minute = 0,
    )

    private fun alarm(
        id: Long,
        title: String,
        repeatRule: RepeatRule,
        nextTriggerAtMillis: Long,
        hour: Int,
        minute: Int,
    ): Alarm = Alarm(
        id = id,
        title = title,
        hour = hour,
        minute = minute,
        repeatRule = repeatRule,
        enabled = true,
        ringtoneUri = AppSettings.DefaultRingtoneUri,
        source = AlarmSource.AI,
        aiOriginalText = "calendar request",
        nextTriggerAtMillis = nextTriggerAtMillis,
        createdAtMillis = 1_000L,
        updatedAtMillis = 1_000L,
        pauseMode = AlarmPauseMode.NONE,
        pausedOccurrenceAtMillis = null,
    )

    private class FakeCalendarSource(
        private val result: CalendarSourceResult,
    ) : CalendarSource {
        override suspend fun loadCalendars(): CalendarSourceResult = result
    }

    private class RecordingCalendarEventWriter(
        private val nextEventId: Long = 9000L,
        private val failure: Throwable? = null,
    ) : CalendarEventWriter {
        val insertedEvents = mutableListOf<CalendarEventInsert>()

        override suspend fun insert(event: CalendarEventInsert): Result<Long> {
            insertedEvents += event
            return failure?.let(Result.Companion::failure) ?: Result.success(nextEventId)
        }
    }

    private class RecordingCalendarEventRepository(
        private val insertFailure: Throwable? = null,
    ) : AlarmCalendarEventRepository {
        val insertedMappings = mutableListOf<AlarmCalendarEventMapping>()

        override suspend fun insert(mapping: AlarmCalendarEventMapping) {
            insertFailure?.let { throw it }
            insertedMappings += mapping
        }

        override suspend fun getByAlarmId(alarmId: Long): AlarmCalendarEventMapping? =
            insertedMappings.firstOrNull { it.alarmId == alarmId }

        override suspend fun deleteByAlarmId(alarmId: Long) {
            insertedMappings.removeAll { it.alarmId == alarmId }
        }
    }
}
