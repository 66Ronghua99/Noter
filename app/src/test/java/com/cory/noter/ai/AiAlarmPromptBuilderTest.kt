package com.cory.noter.ai

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class AiAlarmPromptBuilderTest {
    @Test
    fun `system prompt includes stable rules without per request context`() {
        val prompt = AiAlarmPromptBuilder().buildSystemPrompt()

        assertThat(prompt).doesNotContain("tomorrow morning remind me to take medicine")
        assertThat(prompt).doesNotContain("2026-04-23")
        assertThat(prompt).doesNotContain("15:45")
        assertThat(prompt).doesNotContain("Asia/Shanghai")
        assertThat(prompt).contains("once")
        assertThat(prompt).contains("daily")
        assertThat(prompt).contains("weekdays")
        assertThat(prompt).contains("custom_weekdays")
        assertThat(prompt).contains("weekly_interval")
        assertThat(prompt).contains("repeatRule.endDate")
        assertThat(prompt).contains("one year after repeatRule.startDate")
        assertThat(prompt).contains("ISO weekday numbering")
        assertThat(prompt).contains("Monday is 1")
        assertThat(prompt).contains("Sunday is 7")
        assertThat(prompt).contains("Call create_alarm")
        assertThat(prompt).contains("calendarSync")
        assertThat(prompt).contains("durationMinutes")
        assertThat(prompt).contains("1 and 1440")
        assertThat(prompt).contains("30 minutes")
        assertThat(prompt).contains("Use the user's language")
        assertThat(prompt).contains("Call list_alarms when the user asks to list alarms")
        assertThat(prompt).contains("Call list_alarms before pause_alarm or resume_alarm when the target alarm is unknown")
        assertThat(prompt).contains("Only call end_task after list_alarms for direct list requests")
        assertThat(prompt).contains("After list_alarms for a pause or resume request")
        assertThat(prompt).contains("Call pause_alarm")
        assertThat(prompt).contains("Call resume_alarm")
        assertThat(prompt).contains("Call reject_unclear_request")
        assertThat(prompt).contains("Call end_task")
        assertThat(prompt).contains("ambiguous or missing alarm target")
        assertThat(prompt).contains("poor voice transcript")
        assertThat(prompt).doesNotContain("submit_alarm_draft")
        assertThat(prompt).doesNotContain("Return only JSON")
    }

    @Test
    fun `user message includes request and local context`() {
        val now = ZonedDateTime.of(2026, 4, 23, 15, 45, 0, 0, ZoneId.of("Asia/Shanghai"))

        val message = AiAlarmPromptBuilder().buildUserMessage(
            userRequest = "tomorrow morning remind me to take medicine",
            now = now,
        )

        assertThat(message).contains("tomorrow morning remind me to take medicine")
        assertThat(message).contains("Current local date: 2026-04-23")
        assertThat(message).contains("Current local time: 15:45")
        assertThat(message).contains("Timezone: Asia/Shanghai")
    }
}
