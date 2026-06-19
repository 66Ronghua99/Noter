package com.cory.noter.data.alarm

import com.cory.noter.domain.alarm.RepeatRule
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Test

class RepeatRuleCodecTest {
    private val codec = RepeatRuleCodec()

    @Test
    fun `once repeat rule round trips through columns`() {
        val encoded = codec.encode(RepeatRule.Once(LocalDate.of(2026, 4, 24)))

        assertThat(encoded.repeatType).isEqualTo("once")
        assertThat(encoded.daysOfWeekCsv).isEmpty()
        assertThat(encoded.onceDate).isEqualTo("2026-04-24")

        val decoded = codec.decode(
            repeatType = encoded.repeatType,
            daysOfWeekCsv = encoded.daysOfWeekCsv,
            onceDate = encoded.onceDate,
        )

        assertThat(decoded).isEqualTo(RepeatRule.Once(LocalDate.of(2026, 4, 24)))
    }

    @Test
    fun `daily repeat rule round trips through columns`() {
        val encoded = codec.encode(RepeatRule.Daily)

        assertThat(encoded.repeatType).isEqualTo("daily")
        assertThat(encoded.daysOfWeekCsv).isEmpty()
        assertThat(encoded.onceDate).isNull()

        val decoded = codec.decode(
            repeatType = encoded.repeatType,
            daysOfWeekCsv = encoded.daysOfWeekCsv,
            onceDate = encoded.onceDate,
        )

        assertThat(decoded).isEqualTo(RepeatRule.Daily)
    }

    @Test
    fun `weekdays repeat rule round trips through columns`() {
        val encoded = codec.encode(RepeatRule.Weekdays)

        assertThat(encoded.repeatType).isEqualTo("weekdays")
        assertThat(encoded.daysOfWeekCsv).isEmpty()
        assertThat(encoded.onceDate).isNull()

        val decoded = codec.decode(
            repeatType = encoded.repeatType,
            daysOfWeekCsv = encoded.daysOfWeekCsv,
            onceDate = encoded.onceDate,
        )

        assertThat(decoded).isEqualTo(RepeatRule.Weekdays)
    }

    @Test
    fun `custom weekdays repeat rule stores sorted iso weekdays`() {
        val encoded = codec.encode(
            RepeatRule.CustomWeekdays(
                setOf(
                    DayOfWeek.SUNDAY,
                    DayOfWeek.MONDAY,
                    DayOfWeek.WEDNESDAY,
                ),
            ),
        )

        assertThat(encoded.repeatType).isEqualTo("custom_weekdays")
        assertThat(encoded.daysOfWeekCsv).isEqualTo("1,3,7")
        assertThat(encoded.onceDate).isNull()

        val decoded = codec.decode(
            repeatType = encoded.repeatType,
            daysOfWeekCsv = encoded.daysOfWeekCsv,
            onceDate = encoded.onceDate,
        )

        assertThat(decoded).isEqualTo(
            RepeatRule.CustomWeekdays(
                setOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.SUNDAY,
                ),
            ),
        )
    }

    @Test
    fun `weekly interval repeat rule round trips through columns`() {
        val encoded = codec.encode(
            RepeatRule.WeeklyInterval(
                startDate = LocalDate.of(2026, 5, 1),
                endDate = LocalDate.of(2027, 5, 1),
                intervalWeeks = 2,
                days = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY),
            ),
        )

        assertThat(encoded.repeatType).isEqualTo("weekly_interval")
        assertThat(encoded.daysOfWeekCsv).isEqualTo("1,5")
        assertThat(encoded.startDate).isEqualTo("2026-05-01")
        assertThat(encoded.endDate).isEqualTo("2027-05-01")
        assertThat(encoded.intervalWeeks).isEqualTo(2)

        val decoded = codec.decode(
            repeatType = encoded.repeatType,
            daysOfWeekCsv = encoded.daysOfWeekCsv,
            onceDate = encoded.onceDate,
            startDate = encoded.startDate,
            endDate = encoded.endDate,
            intervalWeeks = encoded.intervalWeeks,
        )

        assertThat(decoded).isEqualTo(
            RepeatRule.WeeklyInterval(
                startDate = LocalDate.of(2026, 5, 1),
                endDate = LocalDate.of(2027, 5, 1),
                intervalWeeks = 2,
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            ),
        )
    }

    @Test
    fun `decode rejects once repeat rule without date`() {
        val error = runCatching {
            codec.decode(
                repeatType = "once",
                daysOfWeekCsv = "",
                onceDate = null,
            )
        }.exceptionOrNull()

        assertThat(error).isNotNull()
        assertThat(error).hasMessageThat().contains("onceDate")
    }

    @Test
    fun `decode rejects custom weekdays outside iso weekday range`() {
        val error = runCatching {
            codec.decode(
                repeatType = "custom_weekdays",
                daysOfWeekCsv = "1,8",
                onceDate = null,
            )
        }.exceptionOrNull()

        assertThat(error).isNotNull()
        assertThat(error).hasMessageThat().contains("daysOfWeekCsv")
    }
}
