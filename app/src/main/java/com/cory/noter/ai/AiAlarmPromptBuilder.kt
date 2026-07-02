package com.cory.noter.ai

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class AiAlarmPromptBuilder {
    fun build(userRequest: String, now: ZonedDateTime): String {
        return buildSystemPrompt() + "\n\n" + buildUserMessage(userRequest, now)
    }

    fun buildSystemPrompt(): String {
        return """
            You convert and manage a user's alarm request for a local Android alarm app.

            Allowed repeatRule.type values: once, daily, weekdays, custom_weekdays, weekly_interval.
            Use ISO weekday numbering for repeatRule.daysOfWeek: Monday is 1 and Sunday is 7.
            For once alarms, include date as an ISO local date in yyyy-MM-dd format.
            For repeating alarms, omit date or set it only when useful for diagnostics; the app ignores date for scheduling repeat rules.
            For weekly_interval alarms, include repeatRule.startDate as yyyy-MM-dd, repeatRule.intervalWeeks as a positive integer, and repeatRule.daysOfWeek as the active weekdays.
            For weekly_interval alarms, include repeatRule.endDate as yyyy-MM-dd when the user gives an end date; if the user does not mention an end date, set repeatRule.endDate to one year after repeatRule.startDate.
            Include calendarSync in every create_alarm call.
            Set calendarSync.enabled to true when the user explicitly asks to add the alarm to a calendar or when the request clearly describes a calendar-like event.
            Use calendarSync.reason values explicit_user_request, calendar_like_event, or none.
            Use calendarSync.durationMinutes between 1 and 1440. If duration is missing, use 30 minutes.
            Set calendarSync.enabled to false and calendarSync.reason to none when the alarm should not be synced to a calendar.
            Use the user's language for user-facing natural-language fields such as title, confirmation text, and rejection reasons when the user's language is clear.
            Do not localize structured fields such as status values, enum values, ids, dates, or calendarSync.reason.
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

    fun buildUserMessage(userRequest: String, now: ZonedDateTime): String {
        val localDate = now.toLocalDate()
        val localTime = now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        val timezone = now.zone.id

        return """
            User request:
            $userRequest

            Current local date: $localDate
            Current local time: $localTime
            Timezone: $timezone
        """.trimIndent()
    }
}
