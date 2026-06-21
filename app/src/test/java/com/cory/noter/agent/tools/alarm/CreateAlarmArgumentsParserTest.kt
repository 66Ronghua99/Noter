package com.cory.noter.agent.tools.alarm

import com.cory.noter.domain.alarm.RepeatRule
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Test

class CreateAlarmArgumentsParserTest {
    private val parser = CreateAlarmArgumentsParser()

    @Test
    fun `parses valid once alarm arguments`() {
        val json = validOnceArguments()

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
    fun `rejects invalid json`() {
        val result = parser.parse("not json")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("Invalid JSON")
    }

    @Test
    fun `rejects unexpected keys`() {
        val result = parser.parse(
            """
            {
              "title": "Take medicine",
              "hour": 8,
              "minute": 30,
              "repeatRule": {
                "type": "once",
                "daysOfWeek": [],
                "startDate": null,
                "endDate": null,
                "intervalWeeks": null,
                "unexpected": "do not accept"
              },
              "date": "2026-04-24",
              "confidence": 0.92,
              "needsClarification": false,
              "clarificationReason": ""
            }
            """.trimIndent(),
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("repeatRule unexpected key")
    }

    @Test
    fun `rejects top level unexpected keys`() {
        val result = parser.parse(
            """
            {
              "title": "Take medicine",
              "hour": 8,
              "minute": 30,
              "repeatRule": {
                "type": "once",
                "daysOfWeek": [],
                "startDate": null,
                "endDate": null,
                "intervalWeeks": null
              },
              "date": "2026-04-24",
              "confidence": 0.92,
              "needsClarification": false,
              "clarificationReason": "",
              "unexpected": "do not accept"
            }
            """.trimIndent(),
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("unexpected key")
    }

    @Test
    fun `rejects missing clarificationReason`() {
        val result = parser.parse(
            """
            {
              "title": "Take medicine",
              "hour": 8,
              "minute": 30,
              "repeatRule": {
                "type": "once",
                "daysOfWeek": [],
                "startDate": null,
                "endDate": null,
                "intervalWeeks": null
              },
              "date": "2026-04-24",
              "confidence": 0.92,
              "needsClarification": false
            }
            """.trimIndent(),
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("clarificationReason")
    }

    @Test
    fun `rejects negative confidence`() {
        val result = parser.parse(
            """
            {
              "title": "Take medicine",
              "hour": 8,
              "minute": 30,
              "repeatRule": {
                "type": "once",
                "daysOfWeek": [],
                "startDate": null,
                "endDate": null,
                "intervalWeeks": null
              },
              "date": "2026-04-24",
              "confidence": -0.1,
              "needsClarification": false,
              "clarificationReason": ""
            }
            """.trimIndent(),
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("confidence")
    }

    @Test
    fun `rejects confidence greater than one`() {
        val result = parser.parse(
            """
            {
              "title": "Take medicine",
              "hour": 8,
              "minute": 30,
              "repeatRule": {
                "type": "once",
                "daysOfWeek": [],
                "startDate": null,
                "endDate": null,
                "intervalWeeks": null
              },
              "date": "2026-04-24",
              "confidence": 1.1,
              "needsClarification": false,
              "clarificationReason": ""
            }
            """.trimIndent(),
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("confidence")
    }

    @Test
    fun `rejects weekday range outside iso bounds`() {
        val result = parser.parse(
            """
            {
              "title": "Workout",
              "hour": 7,
              "minute": 0,
              "repeatRule": {
                "type": "custom_weekdays",
                "daysOfWeek": [1, 8],
                "startDate": null,
                "endDate": null,
                "intervalWeeks": null
              },
              "date": null,
              "confidence": 0.9,
              "needsClarification": false,
              "clarificationReason": ""
            }
            """.trimIndent(),
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("daysOfWeek")
    }

    @Test
    fun `rejects non string weekly interval end date`() {
        val result = parser.parse(
            """
            {
              "title": "Stretch",
              "hour": 7,
              "minute": 15,
              "repeatRule": {
                "type": "weekly_interval",
                "daysOfWeek": [1, 3, 5],
                "startDate": "2026-04-24",
                "endDate": 3,
                "intervalWeeks": 2
              },
              "date": "",
              "confidence": 0.88,
              "needsClarification": false,
              "clarificationReason": ""
            }
            """.trimIndent(),
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("endDate")
    }

    @Test
    fun `rejects invalid weekly interval fields`() {
        val result = parser.parse(
            """
            {
              "title": "Stretch",
              "hour": 7,
              "minute": 15,
              "repeatRule": {
                "type": "weekly_interval",
                "daysOfWeek": [1, 3, 5],
                "startDate": "2026-04-24",
                "endDate": "2026-04-20",
                "intervalWeeks": 0
              },
              "date": "",
              "confidence": 0.88,
              "needsClarification": false,
              "clarificationReason": ""
            }
            """.trimIndent(),
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("intervalWeeks")
    }

    @Test
    fun `rejects invalid hour`() {
        val result = parser.parse(
            """
            {
              "title": "Take medicine",
              "hour": 24,
              "minute": 30,
              "repeatRule": {
                "type": "once",
                "daysOfWeek": [],
                "startDate": null,
                "endDate": null,
                "intervalWeeks": null
              },
              "date": "2026-04-24",
              "confidence": 0.92,
              "needsClarification": false,
              "clarificationReason": ""
            }
            """.trimIndent(),
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("hour")
    }

    @Test
    fun `rejects missing date for once alarm`() {
        val result = parser.parse(
            """
            {
              "title": "Take medicine",
              "hour": 8,
              "minute": 30,
              "repeatRule": {
                "type": "once",
                "daysOfWeek": [],
                "startDate": null,
                "endDate": null,
                "intervalWeeks": null
              },
              "confidence": 0.92,
              "needsClarification": false,
              "clarificationReason": ""
            }
            """.trimIndent(),
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("date")
    }

    @Test
    fun `rejects invalid repeat rule`() {
        val result = parser.parse(
            """
            {
              "title": "Take medicine",
              "hour": 8,
              "minute": 30,
              "repeatRule": {
                "type": "monthly",
                "daysOfWeek": [],
                "startDate": null,
                "endDate": null,
                "intervalWeeks": null
              },
              "date": "2026-04-24",
              "confidence": 0.92,
              "needsClarification": false,
              "clarificationReason": ""
            }
            """.trimIndent(),
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("repeatRule.type")
    }

    @Test
    fun `weekly interval without end date defaults to one year after start date`() {
        val result = parser.parse(
            """
            {
              "title": "Stretch",
              "hour": 7,
              "minute": 15,
              "repeatRule": {
                "type": "weekly_interval",
                "daysOfWeek": [1, 3, 5],
                "startDate": "2026-04-24",
                "endDate": null,
                "intervalWeeks": 2
              },
              "date": "",
              "confidence": 0.88,
              "needsClarification": false,
              "clarificationReason": ""
            }
            """.trimIndent(),
        )

        val draft = result.getOrThrow()
        assertThat(draft.repeatRule).isEqualTo(
            RepeatRule.WeeklyInterval(
                startDate = LocalDate.of(2026, 4, 24),
                endDate = LocalDate.of(2027, 4, 24),
                intervalWeeks = 2,
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            ),
        )
        assertThat(draft.originalDate).isNull()
    }

    @Test
    fun `clarification required throws distinct exception`() {
        val result = parser.parse(
            """
            {
              "title": "",
              "hour": 8,
              "minute": 30,
              "repeatRule": {
                "type": "once",
                "daysOfWeek": [],
                "startDate": null,
                "endDate": null,
                "intervalWeeks": null
              },
              "date": "2026-04-24",
              "confidence": 0.2,
              "needsClarification": true,
              "clarificationReason": "Which day should I use?"
            }
            """.trimIndent(),
        )

        val error = result.exceptionOrNull()
        assertThat(error).isInstanceOf(CreateAlarmArgumentsParser.ClarificationRequiredException::class.java)
        assertThat((error as CreateAlarmArgumentsParser.ClarificationRequiredException).reason)
            .isEqualTo("Which day should I use?")
    }

    private fun validOnceArguments(): String = """
        {
          "title": "Take medicine",
          "hour": 8,
          "minute": 30,
          "repeatRule": {
            "type": "once",
            "daysOfWeek": [],
            "startDate": null,
            "endDate": null,
            "intervalWeeks": null
          },
          "date": "2026-04-24",
          "confidence": 0.92,
          "needsClarification": false,
          "clarificationReason": ""
        }
    """.trimIndent()
}
