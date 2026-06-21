package com.cory.noter.ai

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class AiAlarmPromptBuilder {
    fun build(userRequest: String, now: ZonedDateTime): String {
        val localDate = now.toLocalDate()
        val localTime = now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        val timezone = now.zone.id

        return """
            You convert a user's alarm request into an alarm draft for a local Android alarm app.

            User request:
            $userRequest

            Current local date: $localDate
            Current local time: $localTime
            Timezone: $timezone

            Allowed repeatRule.type values: once, daily, weekdays, custom_weekdays, weekly_interval.
            Use ISO weekday numbering for repeatRule.daysOfWeek: Monday is 1 and Sunday is 7.
            For once alarms, include date as an ISO local date in yyyy-MM-dd format.
            For repeating alarms, omit date or set it only when useful for diagnostics; the app ignores date for scheduling repeat rules.
            For weekly_interval alarms, include repeatRule.startDate as yyyy-MM-dd, repeatRule.intervalWeeks as a positive integer, and repeatRule.daysOfWeek as the active weekdays.
            For weekly_interval alarms, include repeatRule.endDate as yyyy-MM-dd when the user gives an end date; if the user does not mention an end date, set repeatRule.endDate to one year after repeatRule.startDate.
            If the request is ambiguous, set "needsClarification" to true and explain the missing detail in "clarificationReason".
            Call create_alarm with the alarm arguments. Do not answer with prose before the tool call.
        """.trimIndent()
    }
}
