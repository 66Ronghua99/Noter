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

            Allowed repeatRule.type values: once, daily, weekdays, custom_weekdays.
            Use ISO weekday numbering for repeatRule.daysOfWeek: Monday is 1 and Sunday is 7.
            For once alarms, include date as an ISO local date in yyyy-MM-dd format.
            For repeating alarms, omit date or set it only when useful for diagnostics; the app ignores date for scheduling repeat rules.
            If the request is ambiguous, set "needsClarification" to true and explain the missing detail in "clarificationReason".
            Call submit_alarm_draft with the alarm draft arguments. Do not answer with prose.
        """.trimIndent()
    }
}
