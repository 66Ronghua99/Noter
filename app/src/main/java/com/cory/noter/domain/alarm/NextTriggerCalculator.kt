package com.cory.noter.domain.alarm

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class NextTriggerCalculator {
    fun nextTrigger(
        hour: Int,
        minute: Int,
        repeatRule: RepeatRule,
        now: Instant,
        zoneId: ZoneId,
    ): Instant? {
        val time = LocalTime.of(hour, minute)
        val nowAtZone = now.atZone(zoneId)

        return when (repeatRule) {
            is RepeatRule.Once -> {
                val candidate = ZonedDateTime.of(repeatRule.date, time, zoneId)
                candidate.toInstant().takeIf { it.isAfter(now) }
            }

            RepeatRule.Daily -> nextOnOrAfter(
                startDate = nowAtZone.toLocalDate(),
                time = time,
                days = DayOfWeek.entries.toSet(),
                now = now,
                zoneId = zoneId,
            )

            RepeatRule.Weekdays -> nextOnOrAfter(
                startDate = nowAtZone.toLocalDate(),
                time = time,
                days = WEEKDAYS,
                now = now,
                zoneId = zoneId,
            )

            is RepeatRule.CustomWeekdays -> nextOnOrAfter(
                startDate = nowAtZone.toLocalDate(),
                time = time,
                days = repeatRule.days,
                now = now,
                zoneId = zoneId,
            )

            is RepeatRule.WeeklyInterval -> nextWeeklyInterval(
                repeatRule = repeatRule,
                time = time,
                now = now,
                zoneId = zoneId,
            )
        }
    }

    private fun nextOnOrAfter(
        startDate: LocalDate,
        time: LocalTime,
        days: Set<DayOfWeek>,
        now: Instant,
        zoneId: ZoneId,
    ): Instant? {
        require(days.isNotEmpty()) { "Repeating alarms require at least one weekday." }

        for (offset in 0..7) {
            val date = startDate.plusDays(offset.toLong())
            if (date.dayOfWeek in days) {
                val candidate = ZonedDateTime.of(date, time, zoneId).toInstant()
                if (candidate.isAfter(now)) {
                    return candidate
                }
            }
        }

        error("Unable to calculate next trigger within one week.")
    }

    private fun nextWeeklyInterval(
        repeatRule: RepeatRule.WeeklyInterval,
        time: LocalTime,
        now: Instant,
        zoneId: ZoneId,
    ): Instant? {
        require(repeatRule.days.isNotEmpty()) { "Repeating alarms require at least one weekday." }
        require(repeatRule.intervalWeeks > 0) { "intervalWeeks must be greater than zero." }
        require(!repeatRule.endDate.isBefore(repeatRule.startDate)) {
            "endDate must be on or after startDate."
        }

        val start = maxOf(repeatRule.startDate, now.atZone(zoneId).toLocalDate())
        val anchorWeek = repeatRule.firstMatchingDateOnOrAfterStart()?.startOfIsoWeek()
            ?: return null
        val dayCount = ChronoUnit.DAYS.between(start, repeatRule.endDate).coerceAtLeast(0)

        for (offset in 0..dayCount) {
            val date = start.plusDays(offset)
            if (date.dayOfWeek !in repeatRule.days) {
                continue
            }
            val weeksBetween = ChronoUnit.WEEKS.between(anchorWeek, date.startOfIsoWeek())
            if (weeksBetween % repeatRule.intervalWeeks != 0L) {
                continue
            }
            val candidate = ZonedDateTime.of(date, time, zoneId).toInstant()
            if (candidate.isAfter(now)) {
                return candidate
            }
        }

        return null
    }

    private fun RepeatRule.WeeklyInterval.firstMatchingDateOnOrAfterStart(): LocalDate? {
        val dayCount = ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(0)
        for (offset in 0..dayCount) {
            val date = startDate.plusDays(offset)
            if (date.dayOfWeek in days) {
                return date
            }
        }
        return null
    }

    private fun LocalDate.startOfIsoWeek(): LocalDate = minusDays((dayOfWeek.value - 1).toLong())

    private companion object {
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
    }
}
