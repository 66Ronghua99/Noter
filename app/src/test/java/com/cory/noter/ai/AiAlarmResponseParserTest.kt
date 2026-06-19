package com.cory.noter.ai

import com.cory.noter.domain.alarm.RepeatRule
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Test

class AiAlarmResponseParserTest {
    private val parser = AiAlarmResponseParser()

    @Test
    fun `valid once response parses to ai draft`() {
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
    fun `invalid json fails without partial draft`() {
        val result = parser.parse("not json")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("Invalid JSON")
    }

    @Test
    fun `extra top level key fails strict response shape`() {
        val json = """
            {
              "title": "Take medicine",
              "hour": 8,
              "minute": 30,
              "repeatRule": { "type": "once", "daysOfWeek": [] },
              "date": "2026-04-24",
              "confidence": 0.92,
              "needsClarification": false,
              "clarificationReason": "",
              "unexpected": "do not accept"
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("unexpected key")
    }

    @Test
    fun `extra repeat rule key fails strict response shape`() {
        val json = """
            {
              "title": "Take medicine",
              "hour": 8,
              "minute": 30,
              "repeatRule": {
                "type": "once",
                "daysOfWeek": [],
                "unexpected": "do not accept"
              },
              "date": "2026-04-24",
              "confidence": 0.92,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("repeatRule unexpected key")
    }

    @Test
    fun `missing clarification reason fails declared response shape`() {
        val json = """
            {
              "title": "Take medicine",
              "hour": 8,
              "minute": 30,
              "repeatRule": { "type": "once", "daysOfWeek": [] },
              "date": "2026-04-24",
              "confidence": 0.92,
              "needsClarification": false
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("clarificationReason")
    }

    @Test
    fun `invalid time fails`() {
        val json = """
            {
              "title": "Take medicine",
              "hour": 24,
              "minute": 30,
              "repeatRule": { "type": "once", "daysOfWeek": [] },
              "date": "2026-04-24",
              "confidence": 0.92,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("hour")
    }

    @Test
    fun `invalid repeat rule fails`() {
        val json = """
            {
              "title": "Take medicine",
              "hour": 8,
              "minute": 30,
              "repeatRule": { "type": "monthly", "daysOfWeek": [] },
              "date": "2026-04-24",
              "confidence": 0.92,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("repeatRule.type")
    }

    @Test
    fun `once response without date fails`() {
        val json = """
            {
              "title": "Take medicine",
              "hour": 8,
              "minute": 30,
              "repeatRule": { "type": "once", "daysOfWeek": [] },
              "confidence": 0.92,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("date")
    }

    @Test
    fun `needs clarification fails with reason`() {
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

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("Which day should I use?")
    }

    @Test
    fun `needs clarification returns distinct machine readable failure`() {
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

    @Test
    fun `blank clarification reason fails when clarification is required`() {
        val json = """
            {
              "title": "",
              "hour": 8,
              "minute": 30,
              "repeatRule": { "type": "once", "daysOfWeek": [] },
              "date": "2026-04-24",
              "confidence": 0.2,
              "needsClarification": true,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("clarificationReason")
    }

    @Test
    fun `repeating response ignores date after validation`() {
        val json = """
            {
              "title": "Standup",
              "hour": 9,
              "minute": 15,
              "repeatRule": { "type": "weekdays", "daysOfWeek": [] },
              "date": "2026-04-24",
              "confidence": 0.87,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        val draft = result.getOrThrow()
        assertThat(draft.repeatRule).isEqualTo(RepeatRule.Weekdays)
        assertThat(draft.originalDate).isEqualTo(LocalDate.of(2026, 4, 24))
    }

    @Test
    fun `repeating response with null date parses because date only applies to once alarms`() {
        val json = """
            {
              "title": "Standup",
              "hour": 9,
              "minute": 15,
              "repeatRule": { "type": "weekdays", "daysOfWeek": [] },
              "date": null,
              "confidence": 0.87,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        val draft = result.getOrThrow()
        assertThat(draft.repeatRule).isEqualTo(RepeatRule.Weekdays)
        assertThat(draft.originalDate).isNull()
    }

    @Test
    fun `repeating response with blank date parses because date only applies to once alarms`() {
        val json = """
            {
              "title": "Standup",
              "hour": 9,
              "minute": 15,
              "repeatRule": { "type": "weekdays", "daysOfWeek": [] },
              "date": "",
              "confidence": 0.87,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        val draft = result.getOrThrow()
        assertThat(draft.repeatRule).isEqualTo(RepeatRule.Weekdays)
        assertThat(draft.originalDate).isNull()
    }

    @Test
    fun `weekly response ignores blank date because date only applies to once alarms`() {
        val json = """
            {
              "title": "Take medicine",
              "hour": 8,
              "minute": 0,
              "repeatRule": { "type": "custom_weekdays", "daysOfWeek": [1] },
              "date": "",
              "confidence": 0.91,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        val draft = result.getOrThrow()
        assertThat(draft.repeatRule).isEqualTo(RepeatRule.CustomWeekdays(setOf(DayOfWeek.MONDAY)))
        assertThat(draft.originalDate).isNull()
    }

    @Test
    fun `repeating response with invalid date string parses because date only applies to once alarms`() {
        val json = """
            {
              "title": "Standup",
              "hour": 9,
              "minute": 15,
              "repeatRule": { "type": "weekdays", "daysOfWeek": [] },
              "date": "tomorrow",
              "confidence": 0.87,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        val draft = result.getOrThrow()
        assertThat(draft.repeatRule).isEqualTo(RepeatRule.Weekdays)
        assertThat(draft.originalDate).isNull()
    }

    @Test
    fun `infinite confidence fails`() {
        val json = """
            {
              "title": "Standup",
              "hour": 9,
              "minute": 15,
              "repeatRule": { "type": "weekdays", "daysOfWeek": [] },
              "confidence": 1e309,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("confidence")
    }

    @Test
    fun `negative confidence fails`() {
        val json = """
            {
              "title": "Standup",
              "hour": 9,
              "minute": 15,
              "repeatRule": { "type": "weekdays", "daysOfWeek": [] },
              "confidence": -0.1,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("confidence")
    }

    @Test
    fun `confidence greater than one fails`() {
        val json = """
            {
              "title": "Standup",
              "hour": 9,
              "minute": 15,
              "repeatRule": { "type": "weekdays", "daysOfWeek": [] },
              "confidence": 1.1,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("confidence")
    }

    @Test
    fun `custom weekdays rejects days outside iso weekday range`() {
        val json = """
            {
              "title": "Workout",
              "hour": 7,
              "minute": 0,
              "repeatRule": { "type": "custom_weekdays", "daysOfWeek": [1, 8] },
              "confidence": 0.9,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("daysOfWeek")
    }

    @Test
    fun `weekly interval response parses with explicit end date`() {
        val json = """
            {
              "title": "Pay rent",
              "hour": 9,
              "minute": 0,
              "repeatRule": {
                "type": "weekly_interval",
                "daysOfWeek": [1],
                "startDate": "2026-05-01",
                "endDate": "2026-08-01",
                "intervalWeeks": 2
              },
              "confidence": 0.9,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        val draft = result.getOrThrow()
        assertThat(draft.repeatRule).isEqualTo(
            RepeatRule.WeeklyInterval(
                startDate = LocalDate.of(2026, 5, 1),
                endDate = LocalDate.of(2026, 8, 1),
                intervalWeeks = 2,
                days = setOf(DayOfWeek.MONDAY),
            ),
        )
    }

    @Test
    fun `weekly interval response defaults missing end date to one year after start date`() {
        val json = """
            {
              "title": "Water plants",
              "hour": 9,
              "minute": 0,
              "repeatRule": {
                "type": "weekly_interval",
                "daysOfWeek": [1],
                "startDate": "2026-05-01",
                "intervalWeeks": 2
              },
              "confidence": 0.9,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        val draft = result.getOrThrow()
        assertThat(draft.repeatRule).isEqualTo(
            RepeatRule.WeeklyInterval(
                startDate = LocalDate.of(2026, 5, 1),
                endDate = LocalDate.of(2027, 5, 1),
                intervalWeeks = 2,
                days = setOf(DayOfWeek.MONDAY),
            ),
        )
    }

    @Test
    fun `weekly interval response rejects empty days`() {
        val json = """
            {
              "title": "Water plants",
              "hour": 9,
              "minute": 0,
              "repeatRule": {
                "type": "weekly_interval",
                "daysOfWeek": [],
                "startDate": "2026-05-01",
                "intervalWeeks": 2
              },
              "confidence": 0.9,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("daysOfWeek")
    }

    @Test
    fun `weekly interval response rejects missing start date`() {
        val json = """
            {
              "title": "Water plants",
              "hour": 9,
              "minute": 0,
              "repeatRule": {
                "type": "weekly_interval",
                "daysOfWeek": [1],
                "intervalWeeks": 2
              },
              "confidence": 0.9,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("startDate is required")
    }

    @Test
    fun `weekly interval response rejects non positive interval`() {
        val json = """
            {
              "title": "Water plants",
              "hour": 9,
              "minute": 0,
              "repeatRule": {
                "type": "weekly_interval",
                "daysOfWeek": [1],
                "startDate": "2026-05-01",
                "intervalWeeks": 0
              },
              "confidence": 0.9,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("intervalWeeks")
    }

    @Test
    fun `weekly interval response rejects end date before start date`() {
        val json = """
            {
              "title": "Water plants",
              "hour": 9,
              "minute": 0,
              "repeatRule": {
                "type": "weekly_interval",
                "daysOfWeek": [1],
                "startDate": "2026-05-01",
                "endDate": "2026-04-30",
                "intervalWeeks": 2
              },
              "confidence": 0.9,
              "needsClarification": false,
              "clarificationReason": ""
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("endDate")
    }
}
