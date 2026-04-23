package com.cory.noter.data.alarm

import com.cory.noter.domain.alarm.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate

class RepeatRuleCodec {
    fun encode(repeatRule: RepeatRule): EncodedRepeatRule = when (repeatRule) {
        is RepeatRule.Once -> EncodedRepeatRule(
            repeatType = "once",
            daysOfWeekCsv = "",
            onceDate = repeatRule.date.toString(),
        )

        RepeatRule.Daily -> EncodedRepeatRule(
            repeatType = "daily",
            daysOfWeekCsv = "",
            onceDate = null,
        )

        RepeatRule.Weekdays -> EncodedRepeatRule(
            repeatType = "weekdays",
            daysOfWeekCsv = "",
            onceDate = null,
        )

        is RepeatRule.CustomWeekdays -> EncodedRepeatRule(
            repeatType = "custom_weekdays",
            daysOfWeekCsv = repeatRule.days
                .map { it.value }
                .sorted()
                .joinToString(","),
            onceDate = null,
        )
    }

    fun decode(
        repeatType: String,
        daysOfWeekCsv: String,
        onceDate: String?,
    ): RepeatRule = when (repeatType) {
        "once" -> RepeatRule.Once(
            requireNotNull(onceDate) { "onceDate is required for once repeat rule." }
                .let(LocalDate::parse),
        )

        "daily" -> {
            require(daysOfWeekCsv.isBlank()) { "daysOfWeekCsv must be empty for daily repeat rule." }
            RepeatRule.Daily
        }

        "weekdays" -> {
            require(daysOfWeekCsv.isBlank()) { "daysOfWeekCsv must be empty for weekdays repeat rule." }
            RepeatRule.Weekdays
        }

        "custom_weekdays" -> RepeatRule.CustomWeekdays(
            daysOfWeekCsv
                .split(",")
                .filter { it.isNotBlank() }
                .map { rawValue ->
                    val dayNumber = requireNotNull(rawValue.toIntOrNull()) {
                        "daysOfWeekCsv must contain numeric ISO weekday values."
                    }
                    require(dayNumber in 1..7) {
                        "daysOfWeekCsv must contain ISO weekday values from 1 through 7."
                    }
                    DayOfWeek.of(dayNumber)
                }
                .toSet()
                .also { days ->
                    require(days.isNotEmpty()) {
                        "daysOfWeekCsv must contain at least one ISO weekday for custom_weekdays."
                    }
                },
        )

        else -> error("Unsupported repeatType: $repeatType")
    }

    data class EncodedRepeatRule(
        val repeatType: String,
        val daysOfWeekCsv: String,
        val onceDate: String?,
    )
}
