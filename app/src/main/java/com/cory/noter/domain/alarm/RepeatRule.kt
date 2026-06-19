package com.cory.noter.domain.alarm

import java.time.DayOfWeek
import java.time.LocalDate

sealed interface RepeatRule {
    data class Once(val date: LocalDate) : RepeatRule
    data object Daily : RepeatRule
    data object Weekdays : RepeatRule
    data class CustomWeekdays(val days: Set<DayOfWeek>) : RepeatRule
    data class WeeklyInterval(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val intervalWeeks: Int,
        val days: Set<DayOfWeek>,
    ) : RepeatRule
}
