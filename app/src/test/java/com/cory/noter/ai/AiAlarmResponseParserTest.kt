package com.cory.noter.ai

import com.cory.noter.domain.alarm.RepeatRule
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class AiAlarmResponseParserTest {
    private val parser = AiAlarmResponseParser()

    @Test
    fun `valid once response still parses through compatibility shim`() {
        val json = """
            {
              "title": "Take medicine",
              "hour": 8,
              "minute": 30,
              "repeatRule": { "type": "once", "daysOfWeek": [] },
              "date": "2026-04-24",
              "confidence": 0.92,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        val draft = result.getOrThrow()
        assertThat(draft.title).isEqualTo("Take medicine")
        assertThat(draft.hour).isEqualTo(8)
        assertThat(draft.minute).isEqualTo(30)
        assertThat(draft.repeatRule).isEqualTo(RepeatRule.Once(LocalDate.of(2026, 4, 24)))
        assertThat(draft.originalDate).isEqualTo(LocalDate.of(2026, 4, 24))
        assertThat(draft.confidence).isEqualTo(0.92)
        assertThat(draft.originalResponseText).isEqualTo(json)
    }

    @Test
    fun `needs clarification still throws shim exception`() {
        val json = """
            {
              "title": "",
              "hour": 8,
              "minute": 30,
              "repeatRule": { "type": "once", "daysOfWeek": [] },
              "date": "2026-04-24",
              "confidence": 0.2,
              "needsClarification": true,
              "clarificationReason": "Which day should I use?"
            }
        """.trimIndent()

        val result = parser.parse(json)

        val error = result.exceptionOrNull()
        assertThat(error).isInstanceOf(AiAlarmResponseParser.ClarificationRequiredException::class.java)
        assertThat((error as AiAlarmResponseParser.ClarificationRequiredException).reason)
            .isEqualTo("Which day should I use?")
    }
}
