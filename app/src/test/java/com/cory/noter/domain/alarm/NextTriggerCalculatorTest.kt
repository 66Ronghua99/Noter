package com.cory.noter.domain.alarm

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class NextTriggerCalculatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val calculator = NextTriggerCalculator()

    @Test
    fun `once alarm later today schedules today`() {
        val now = ZonedDateTime.of(2026, 4, 23, 9, 0, 0, 0, zone).toInstant()
        val rule = RepeatRule.Once(LocalDate.of(2026, 4, 23))

        val result = calculator.nextTrigger(
            hour = 10,
            minute = 30,
            repeatRule = rule,
            now = now,
            zoneId = zone,
        )

        assertThat(result).isEqualTo(
            ZonedDateTime.of(2026, 4, 23, 10, 30, 0, 0, zone).toInstant(),
        )
    }

    @Test
    fun `once alarm earlier today returns null`() {
        val now = ZonedDateTime.of(2026, 4, 23, 11, 0, 0, 0, zone).toInstant()
        val rule = RepeatRule.Once(LocalDate.of(2026, 4, 23))

        val result = calculator.nextTrigger(
            hour = 10,
            minute = 30,
            repeatRule = rule,
            now = now,
            zoneId = zone,
        )

        assertThat(result).isNull()
    }

    @Test
    fun `daily alarm schedules the next day when time already passed`() {
        val now = ZonedDateTime.of(2026, 4, 23, 11, 0, 0, 0, zone).toInstant()

        val result = calculator.nextTrigger(
            hour = 10,
            minute = 30,
            repeatRule = RepeatRule.Daily,
            now = now,
            zoneId = zone,
        )

        assertThat(result).isEqualTo(
            ZonedDateTime.of(2026, 4, 24, 10, 30, 0, 0, zone).toInstant(),
        )
    }

    @Test
    fun `weekdays alarm skips weekend and schedules next monday`() {
        val now = ZonedDateTime.of(2026, 4, 24, 18, 0, 0, 0, zone).toInstant()

        val result = calculator.nextTrigger(
            hour = 8,
            minute = 15,
            repeatRule = RepeatRule.Weekdays,
            now = now,
            zoneId = zone,
        )

        assertThat(result).isEqualTo(
            ZonedDateTime.of(2026, 4, 27, 8, 15, 0, 0, zone).toInstant(),
        )
        assertThat(result!!.atZone(zone).dayOfWeek.value).isEqualTo(1)
    }

    @Test
    fun `custom weekday alarm schedules matching sunday with iso day seven`() {
        val now = ZonedDateTime.of(2026, 4, 24, 18, 0, 0, 0, zone).toInstant()

        val result = calculator.nextTrigger(
            hour = 7,
            minute = 45,
            repeatRule = RepeatRule.CustomWeekdays(setOf(DayOfWeek.SUNDAY)),
            now = now,
            zoneId = zone,
        )

        assertThat(result).isEqualTo(
            ZonedDateTime.of(2026, 4, 26, 7, 45, 0, 0, zone).toInstant(),
        )
        assertThat(result!!.atZone(zone).dayOfWeek.value).isEqualTo(7)
    }

    @Test
    fun `custom weekday alarm schedules next week when only matching day already passed`() {
        val now = ZonedDateTime.of(2026, 4, 23, 18, 0, 0, 0, zone).toInstant()

        val result = calculator.nextTrigger(
            hour = 7,
            minute = 45,
            repeatRule = RepeatRule.CustomWeekdays(setOf(DayOfWeek.THURSDAY)),
            now = now,
            zoneId = zone,
        )

        assertThat(result).isEqualTo(
            ZonedDateTime.of(2026, 4, 30, 7, 45, 0, 0, zone).toInstant(),
        )
    }
}
