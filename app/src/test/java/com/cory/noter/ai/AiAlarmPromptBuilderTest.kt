package com.cory.noter.ai

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class AiAlarmPromptBuilderTest {
    @Test
    fun `built in model ids match paid tool capable catalog`() {
        assertThat(OpenRouterModel.builtInIds).containsExactly(
            "deepseek/deepseek-v4-flash",
            "deepseek/deepseek-v3.2",
        ).inOrder()
        assertThat(OpenRouterModel.DefaultId).isEqualTo("deepseek/deepseek-v4-flash")
        assertThat(OpenRouterModel.builtInIds.none { it.contains(":free") || it == "openrouter/free" })
            .isTrue()
    }

    @Test
    fun `prompt includes local context allowed rules and tool instructions`() {
        val now = ZonedDateTime.of(2026, 4, 23, 15, 45, 0, 0, ZoneId.of("Asia/Shanghai"))

        val prompt = AiAlarmPromptBuilder().build(
            userRequest = "tomorrow morning remind me to take medicine",
            now = now,
        )

        assertThat(prompt).contains("tomorrow morning remind me to take medicine")
        assertThat(prompt).contains("Current local date: 2026-04-23")
        assertThat(prompt).contains("Current local time: 15:45")
        assertThat(prompt).contains("Timezone: Asia/Shanghai")
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
        assertThat(prompt).contains("Call reject_unclear_request")
        assertThat(prompt).contains("Call end_task")
        assertThat(prompt).contains("poor voice transcript")
        assertThat(prompt).doesNotContain("submit_alarm_draft")
        assertThat(prompt).doesNotContain("Return only JSON")
    }
}
