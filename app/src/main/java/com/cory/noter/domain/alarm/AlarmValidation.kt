package com.cory.noter.domain.alarm

import java.time.Instant
import java.time.ZoneId

object AlarmValidation {
    enum class Error {
        BLANK_TITLE,
        INVALID_HOUR,
        INVALID_MINUTE,
        EMPTY_CUSTOM_WEEKDAYS,
        EMPTY_INTERVAL_WEEKDAYS,
        INVALID_INTERVAL_WEEKS,
        INVALID_INTERVAL_RANGE,
        EXPIRED_ONE_TIME_ALARM,
        EXPIRED_INTERVAL_ALARM,
    }

    fun validateDraft(
        title: String,
        hour: Int,
        minute: Int,
        repeatRule: RepeatRule,
        now: Instant,
        zoneId: ZoneId,
        nextTriggerCalculator: NextTriggerCalculator = NextTriggerCalculator(),
    ): List<Error> {
        val errors = mutableListOf<Error>()

        if (title.isBlank()) {
            errors += Error.BLANK_TITLE
        }

        if (hour !in 0..23) {
            errors += Error.INVALID_HOUR
        }

        if (minute !in 0..59) {
            errors += Error.INVALID_MINUTE
        }

        if (repeatRule is RepeatRule.CustomWeekdays && repeatRule.days.isEmpty()) {
            errors += Error.EMPTY_CUSTOM_WEEKDAYS
        }

        if (repeatRule is RepeatRule.WeeklyInterval) {
            if (repeatRule.days.isEmpty()) {
                errors += Error.EMPTY_INTERVAL_WEEKDAYS
            }
            if (repeatRule.intervalWeeks <= 0) {
                errors += Error.INVALID_INTERVAL_WEEKS
            }
            if (repeatRule.endDate.isBefore(repeatRule.startDate)) {
                errors += Error.INVALID_INTERVAL_RANGE
            }
        }

        val canCalculateTrigger = Error.INVALID_HOUR !in errors &&
            Error.INVALID_MINUTE !in errors &&
            Error.EMPTY_CUSTOM_WEEKDAYS !in errors &&
            Error.EMPTY_INTERVAL_WEEKDAYS !in errors &&
            Error.INVALID_INTERVAL_WEEKS !in errors &&
            Error.INVALID_INTERVAL_RANGE !in errors
        if (repeatRule is RepeatRule.Once && canCalculateTrigger) {
            val nextTrigger = nextTriggerCalculator.nextTrigger(
                hour = hour,
                minute = minute,
                repeatRule = repeatRule,
                now = now,
                zoneId = zoneId,
            )
            if (nextTrigger == null) {
                errors += Error.EXPIRED_ONE_TIME_ALARM
            }
        }
        if (repeatRule is RepeatRule.WeeklyInterval && canCalculateTrigger) {
            val nextTrigger = nextTriggerCalculator.nextTrigger(
                hour = hour,
                minute = minute,
                repeatRule = repeatRule,
                now = now,
                zoneId = zoneId,
            )
            if (nextTrigger == null) {
                errors += Error.EXPIRED_INTERVAL_ALARM
            }
        }

        return errors
    }
}
