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

            Return only JSON. Do not include markdown, comments, code fences, prose, or extra keys.
            The JSON must match this exact shape:
            {
              "title": "Take medicine",
              "hour": 8,
              "minute": 30,
              "repeatRule": {
                "type": "once",
                "daysOfWeek": []
              },
              "date": "2026-04-24",
              "confidence": 0.92,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()
    }
}
