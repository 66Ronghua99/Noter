package com.cory.noter.calendar

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.cory.noter.data.alarm.AlarmCalendarEventMapping
import com.cory.noter.data.alarm.AlarmCalendarEventRepository
import com.cory.noter.data.settings.SettingsRepository
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.RepeatRule
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class CalendarSyncRequest(
    val enabled: Boolean,
    val reason: String,
    val durationMinutes: Int,
)

fun interface CalendarAlarmSyncer {
    suspend fun sync(
        alarm: Alarm,
        request: CalendarSyncRequest,
    ): CalendarSyncResult
}

enum class CalendarSyncStatus(val value: String) {
    SYNCED("synced"),
    SKIPPED("skipped"),
    MISSING_CALENDAR_PERMISSION("missing_calendar_permission"),
    MISSING_DEFAULT_CALENDAR("missing_default_calendar"),
    CALENDAR_NOT_WRITABLE("calendar_not_writable"),
    CALENDAR_INSERT_FAILED("calendar_insert_failed"),
    MAPPING_PERSIST_FAILED("mapping_persist_failed"),
}

sealed interface CalendarSyncResult {
    val status: CalendarSyncStatus

    data object Skipped : CalendarSyncResult {
        override val status: CalendarSyncStatus = CalendarSyncStatus.SKIPPED
    }

    data class Synced(
        val alarmId: Long,
        val calendarId: Long,
        val eventId: Long,
    ) : CalendarSyncResult {
        override val status: CalendarSyncStatus = CalendarSyncStatus.SYNCED
    }

    data object MissingCalendarPermission : CalendarSyncResult {
        override val status: CalendarSyncStatus = CalendarSyncStatus.MISSING_CALENDAR_PERMISSION
    }

    data object MissingDefaultCalendar : CalendarSyncResult {
        override val status: CalendarSyncStatus = CalendarSyncStatus.MISSING_DEFAULT_CALENDAR
    }

    data object CalendarNotWritable : CalendarSyncResult {
        override val status: CalendarSyncStatus = CalendarSyncStatus.CALENDAR_NOT_WRITABLE
    }

    data class CalendarInsertFailed(
        val reason: String,
    ) : CalendarSyncResult {
        override val status: CalendarSyncStatus = CalendarSyncStatus.CALENDAR_INSERT_FAILED
    }

    data class MappingPersistFailed(
        val eventId: Long,
        val reason: String,
    ) : CalendarSyncResult {
        override val status: CalendarSyncStatus = CalendarSyncStatus.MAPPING_PERSIST_FAILED
    }
}

data class CalendarEventInsert(
    val calendarId: Long,
    val title: String,
    val dtStartMillis: Long,
    val dtEndMillis: Long?,
    val duration: String?,
    val rrule: String?,
    val eventTimeZone: String,
)

fun interface CalendarEventWriter {
    suspend fun insert(event: CalendarEventInsert): Result<Long>
}

class AndroidCalendarEventWriter(
    private val context: Context,
) : CalendarEventWriter {
    override suspend fun insert(event: CalendarEventInsert): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, event.calendarId)
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DTSTART, event.dtStartMillis)
                put(CalendarContract.Events.EVENT_TIMEZONE, event.eventTimeZone)
                event.dtEndMillis?.let { put(CalendarContract.Events.DTEND, it) }
                event.duration?.let { put(CalendarContract.Events.DURATION, it) }
                event.rrule?.let { put(CalendarContract.Events.RRULE, it) }
            }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: error("Calendar Provider returned no event URI.")
            requireNotNull(uri.lastPathSegment?.toLongOrNull()) {
                "Calendar Provider returned event URI without numeric id: $uri"
            }
        }
    }
}

class CalendarSyncUseCase(
    private val settingsRepository: SettingsRepository,
    private val calendarSource: CalendarSource,
    private val eventWriter: CalendarEventWriter,
    private val mappingRepository: AlarmCalendarEventRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : CalendarAlarmSyncer {
    override suspend fun sync(
        alarm: Alarm,
        request: CalendarSyncRequest,
    ): CalendarSyncResult {
        if (!request.enabled) {
            return CalendarSyncResult.Skipped
        }

        val defaultCalendarId = settingsRepository.settings.first().defaultCalendarId
            ?: return CalendarSyncResult.MissingDefaultCalendar
        val calendar = when (val calendars = calendarSource.loadCalendars()) {
            CalendarSourceResult.MissingPermission -> return CalendarSyncResult.MissingCalendarPermission
            is CalendarSourceResult.Available -> calendars.calendars.firstOrNull { it.id == defaultCalendarId }
        }
        if (calendar?.writable != true) {
            return CalendarSyncResult.CalendarNotWritable
        }

        val event = runCatching {
            alarm.toCalendarEventInsert(
                calendarId = defaultCalendarId,
                durationMinutes = request.durationMinutes,
                zoneId = zoneIdProvider(),
            )
        }.getOrElse { error ->
            return CalendarSyncResult.CalendarInsertFailed(error.message.orEmpty())
        }
        val eventId = eventWriter.insert(event).getOrElse { error ->
            return CalendarSyncResult.CalendarInsertFailed(error.message.orEmpty())
        }

        val nowMillis = clock.millis()
        val mapping = AlarmCalendarEventMapping(
            alarmId = alarm.id,
            calendarId = defaultCalendarId,
            eventId = eventId,
            durationMinutes = request.durationMinutes,
            reason = request.reason,
            status = CalendarSyncStatus.SYNCED.value,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        )
        return runCatching {
            mappingRepository.insert(mapping)
        }.fold(
            onSuccess = {
                CalendarSyncResult.Synced(
                    alarmId = alarm.id,
                    calendarId = defaultCalendarId,
                    eventId = eventId,
                )
            },
            onFailure = { error ->
                CalendarSyncResult.MappingPersistFailed(
                    eventId = eventId,
                    reason = error.message.orEmpty(),
                )
            },
        )
    }

    private fun Alarm.toCalendarEventInsert(
        calendarId: Long,
        durationMinutes: Int,
        zoneId: ZoneId,
    ): CalendarEventInsert {
        val startMillis = requireNotNull(nextTriggerAtMillis) {
            "Alarm ${id} has no next trigger for calendar sync."
        }
        val duration = "PT${durationMinutes}M"
        val repeatRule = repeatRule
        return if (repeatRule is RepeatRule.Once) {
            CalendarEventInsert(
                calendarId = calendarId,
                title = title,
                dtStartMillis = startMillis,
                dtEndMillis = Instant.ofEpochMilli(startMillis)
                    .plusSeconds(durationMinutes.toLong() * 60L)
                    .toEpochMilli(),
                duration = null,
                rrule = null,
                eventTimeZone = zoneId.id,
            )
        } else {
            CalendarEventInsert(
                calendarId = calendarId,
                title = title,
                dtStartMillis = startMillis,
                dtEndMillis = null,
                duration = duration,
                rrule = repeatRule.toRRule(zoneId),
                eventTimeZone = zoneId.id,
            )
        }
    }

    private fun RepeatRule.toRRule(zoneId: ZoneId): String = when (this) {
        is RepeatRule.Once -> error("One-time alarms do not use RRULE.")
        RepeatRule.Daily -> "FREQ=DAILY"
        RepeatRule.Weekdays -> "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
        is RepeatRule.CustomWeekdays -> "FREQ=WEEKLY;BYDAY=${days.toByDay()}"
        is RepeatRule.WeeklyInterval -> {
            val until = ZonedDateTime.of(endDate, LocalTime.MAX, zoneId)
                .withNano(0)
                .withZoneSameInstant(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
            "FREQ=WEEKLY;INTERVAL=$intervalWeeks;BYDAY=${days.toByDay()};UNTIL=$until"
        }
    }

    private fun Set<DayOfWeek>.toByDay(): String = sortedBy(DayOfWeek::getValue)
        .joinToString(",") { day -> day.toRRuleDay() }

    private fun DayOfWeek.toRRuleDay(): String = when (this) {
        DayOfWeek.MONDAY -> "MO"
        DayOfWeek.TUESDAY -> "TU"
        DayOfWeek.WEDNESDAY -> "WE"
        DayOfWeek.THURSDAY -> "TH"
        DayOfWeek.FRIDAY -> "FR"
        DayOfWeek.SATURDAY -> "SA"
        DayOfWeek.SUNDAY -> "SU"
    }
}
