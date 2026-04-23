package com.cory.noter.ai

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class AiAlarmPromptBuilderTest {
    @Test
    fun `built in model ids match approved free catalog`() {
        assertThat(OpenRouterModel.builtInIds).containsExactly(
            "minimax/minimax-m2.5:free",
            "openrouter/free",
            "qwen/qwen3-next-80b-a3b-instruct:free",
            "z-ai/glm-4.5-air:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "google/gemma-3-27b-it:free",
            "openai/gpt-oss-120b:free",
        ).inOrder()
    }

    @Test
    fun `prompt includes local context allowed rules and json only instruction`() {
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
        assertThat(prompt).contains("ISO weekday numbering")
        assertThat(prompt).contains("Monday is 1")
        assertThat(prompt).contains("Sunday is 7")
        assertThat(prompt).contains("Return only JSON")
        assertThat(prompt).contains("\"needsClarification\"")
    }
}
