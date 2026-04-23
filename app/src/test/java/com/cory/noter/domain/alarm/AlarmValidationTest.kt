package com.cory.noter.domain.alarm

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class AlarmValidationTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 4, 23, 11, 0, 0, 0, zone).toInstant()

    @Test
    fun `valid daily draft returns no errors`() {
        val result = AlarmValidation.validateDraft(
            title = "Take medicine",
            hour = 12,
            minute = 30,
            repeatRule = RepeatRule.Daily,
            now = now,
            zoneId = zone,
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `blank title is rejected`() {
        val result = AlarmValidation.validateDraft(
            title = "   ",
            hour = 12,
            minute = 30,
            repeatRule = RepeatRule.Daily,
            now = now,
            zoneId = zone,
        )

        assertThat(result).containsExactly(AlarmValidation.Error.BLANK_TITLE)
    }

    @Test
    fun `invalid hour is rejected`() {
        val result = AlarmValidation.validateDraft(
            title = "Take medicine",
            hour = 24,
            minute = 30,
            repeatRule = RepeatRule.Daily,
            now = now,
            zoneId = zone,
        )

        assertThat(result).containsExactly(AlarmValidation.Error.INVALID_HOUR)
    }

    @Test
    fun `invalid minute is rejected`() {
        val result = AlarmValidation.validateDraft(
            title = "Take medicine",
            hour = 12,
            minute = 60,
            repeatRule = RepeatRule.Daily,
            now = now,
            zoneId = zone,
        )

        assertThat(result).containsExactly(AlarmValidation.Error.INVALID_MINUTE)
    }

    @Test
    fun `empty custom weekdays are rejected`() {
        val result = AlarmValidation.validateDraft(
            title = "Take medicine",
            hour = 12,
            minute = 30,
            repeatRule = RepeatRule.CustomWeekdays(emptySet()),
            now = now,
            zoneId = zone,
        )

        assertThat(result).containsExactly(AlarmValidation.Error.EMPTY_CUSTOM_WEEKDAYS)
    }

    @Test
    fun `expired one time alarm is rejected`() {
        val result = AlarmValidation.validateDraft(
            title = "Take medicine",
            hour = 10,
            minute = 30,
            repeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 23)),
            now = now,
            zoneId = zone,
        )

        assertThat(result).containsExactly(AlarmValidation.Error.EXPIRED_ONE_TIME_ALARM)
    }

    @Test
    fun `future one time alarm is accepted`() {
        val result = AlarmValidation.validateDraft(
            title = "Take medicine",
            hour = 12,
            minute = 30,
            repeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 23)),
            now = now,
            zoneId = zone,
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `multiple invalid fields return all applicable errors`() {
        val result = AlarmValidation.validateDraft(
            title = "",
            hour = -1,
            minute = 99,
            repeatRule = RepeatRule.CustomWeekdays(setOf(DayOfWeek.MONDAY)),
            now = now,
            zoneId = zone,
        )

        assertThat(result).containsExactly(
            AlarmValidation.Error.BLANK_TITLE,
            AlarmValidation.Error.INVALID_HOUR,
            AlarmValidation.Error.INVALID_MINUTE,
        ).inOrder()
    }
}
