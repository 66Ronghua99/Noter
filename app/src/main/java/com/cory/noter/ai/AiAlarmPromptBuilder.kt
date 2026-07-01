package com.cory.noter.ai

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class AiAlarmPromptBuilder {
    fun build(userRequest: String, now: ZonedDateTime): String {
        val localDate = now.toLocalDate()
        val localTime = now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        val timezone = now.zone.id

        return """
            You convert and manage a user's alarm request for a local Android alarm app.

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
            Call create_alarm with the alarm arguments when the request has enough alarm details.
            Call list_alarms when the user asks to list alarms.
            Only call end_task after list_alarms for direct list requests.
            Call list_alarms before pause_alarm or resume_alarm when the target alarm is unknown.
            After list_alarms for a pause or resume request, call pause_alarm, resume_alarm, or reject_unclear_request; do not end the task with only the list.
            Call pause_alarm with mode next_occurrence or indefinite when the user asks to pause a known alarm.
            Call resume_alarm when the user asks to resume a known paused alarm.
            Call reject_unclear_request when the request is unclear, incomplete, not an alarm request, likely came from a poor voice transcript, or has an ambiguous or missing alarm target.
            Call end_task after create_alarm, pause_alarm, resume_alarm, or reject_unclear_request succeeds to end the task.
            Do not answer with prose before the tool call.
        """.trimIndent()
    }
}
