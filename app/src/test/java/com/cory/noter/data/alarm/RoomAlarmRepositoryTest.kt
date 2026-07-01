package com.cory.noter.data.alarm

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.AlarmPauseMode
import com.cory.noter.domain.alarm.NextTriggerCalculator
import com.cory.noter.domain.alarm.RepeatRule
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomAlarmRepositoryTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val losAngelesZone = ZoneId.of("America/Los_Angeles")
    private lateinit var database: AlarmDatabase
    private lateinit var repository: AlarmRepository
    private lateinit var clock: MutableClock
    private var currentZoneId: ZoneId = zone

    @Before
    fun setUp() {
        clock = MutableClock(
            currentInstant = ZonedDateTime.of(2026, 4, 23, 9, 0, 0, 0, zone).toInstant(),
            zoneId = zone,
        )
        currentZoneId = zone
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AlarmDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        repository = RoomAlarmRepository(
            alarmDao = database.alarmDao(),
            clock = clock,
            nextTriggerCalculator = NextTriggerCalculator(),
            zoneIdProvider = { currentZoneId },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `create persists enabled alarm with computed next trigger`() = runTest {
        val created = repository.create(
            AlarmDraft(
                title = "Take medicine",
                hour = 8,
                minute = 30,
                repeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 24)),
                enabled = true,
                ringtoneUri = "content://settings/system/alarm_alert",
                source = AlarmSource.MANUAL,
                aiOriginalText = null,
            ),
        )

        assertThat(created.id).isGreaterThan(0)
        assertThat(created.nextTriggerAtMillis).isEqualTo(
            ZonedDateTime.of(2026, 4, 24, 8, 30, 0, 0, zone).toInstant().toEpochMilli(),
        )
        assertThat(created.createdAtMillis).isEqualTo(clock.instant().toEpochMilli())
        assertThat(created.updatedAtMillis).isEqualTo(clock.instant().toEpochMilli())
        assertThat(created.pauseMode).isEqualTo(AlarmPauseMode.NONE)
        assertThat(created.pausedOccurrenceAtMillis).isNull()
        assertThat(repository.get(created.id)).isEqualTo(created)
        assertThat(repository.alarms.first()).containsExactly(created)
    }

    @Test
    fun `repository mapping round trips pause state`() = runTest {
        val created = repository.create(dailyAlarmDraft(enabled = true))
        val pausedOccurrenceAtMillis = requireNotNull(created.nextTriggerAtMillis)

        val pausedNext = repository.updateFromManagement(
            created.copy(
                pauseMode = AlarmPauseMode.NEXT_OCCURRENCE,
                pausedOccurrenceAtMillis = pausedOccurrenceAtMillis,
                nextTriggerAtMillis = pausedOccurrenceAtMillis,
            ),
        )
        val pausedIndefinitely = repository.updateFromManagement(
            pausedNext.copy(
                enabled = false,
                nextTriggerAtMillis = null,
                pauseMode = AlarmPauseMode.INDEFINITE,
                pausedOccurrenceAtMillis = null,
            ),
        )
        val active = repository.updateFromManagement(
            pausedIndefinitely.copy(
                enabled = true,
                nextTriggerAtMillis = pausedOccurrenceAtMillis,
                pauseMode = AlarmPauseMode.NONE,
                pausedOccurrenceAtMillis = null,
            ),
        )

        assertThat(pausedNext.pauseMode).isEqualTo(AlarmPauseMode.NEXT_OCCURRENCE)
        assertThat(pausedNext.pausedOccurrenceAtMillis).isEqualTo(pausedOccurrenceAtMillis)
        assertThat(pausedIndefinitely.pauseMode).isEqualTo(AlarmPauseMode.INDEFINITE)
        assertThat(pausedIndefinitely.pausedOccurrenceAtMillis).isNull()
        assertThat(active.pauseMode).isEqualTo(AlarmPauseMode.NONE)
        assertThat(active.pausedOccurrenceAtMillis).isNull()
    }

    @Test
    fun `management update preserves provided next trigger and pause anchor`() = runTest {
        val created = repository.create(dailyAlarmDraft(enabled = true))
        val originalTriggerAtMillis = requireNotNull(created.nextTriggerAtMillis)
        clock.set(ZonedDateTime.of(2026, 4, 25, 9, 0, 0, 0, zone).toInstant())

        val updated = repository.updateFromManagement(
            created.copy(
                pauseMode = AlarmPauseMode.NEXT_OCCURRENCE,
                pausedOccurrenceAtMillis = originalTriggerAtMillis,
                nextTriggerAtMillis = originalTriggerAtMillis,
            ),
        )

        assertThat(updated.pauseMode).isEqualTo(AlarmPauseMode.NEXT_OCCURRENCE)
        assertThat(updated.pausedOccurrenceAtMillis).isEqualTo(originalTriggerAtMillis)
        assertThat(updated.nextTriggerAtMillis).isEqualTo(originalTriggerAtMillis)
    }

    @Test
    fun `loading unknown stored pause mode fails explicitly`() = runTest {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO alarms (
                id, title, hour, minute, repeatType, daysOfWeekCsv, onceDate,
                startDate, endDate, intervalWeeks, enabled, ringtoneUri, source,
                aiOriginalText, nextTriggerAtMillis, pauseMode, pausedOccurrenceAtMillis,
                createdAtMillis, updatedAtMillis
            ) VALUES (
                42, 'Bad pause', 8, 0, 'daily', '', NULL,
                NULL, NULL, NULL, 1, 'content://settings/system/alarm_alert', 'manual',
                NULL, 1234, 'temporary', NULL, 1000, 1000
            )
            """.trimIndent(),
        )

        val error = runCatching { repository.get(42) }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat().contains("Unsupported alarm pause mode")
    }

    @Test
    fun `migration 2 to 3 maps enabled rows to none pause mode and disabled rows to indefinite`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(MIGRATION_TEST_DB)
        openHelper(version = 2, onCreate = ::createVersion2Database).apply {
            writableDatabase.close()
            close()
        }

        val db = openHelper(
            version = 3,
            onCreate = {},
            onUpgrade = { database, oldVersion, newVersion ->
                check(oldVersion == 2 && newVersion == 3)
                AlarmDatabase.MIGRATION_2_3.migrate(database)
            },
        ).writableDatabase

        db.query("SELECT id, pauseMode, pausedOccurrenceAtMillis FROM alarms ORDER BY id").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(1)
            assertThat(cursor.getString(1)).isEqualTo("none")
            assertThat(cursor.isNull(2)).isTrue()

            assertThat(cursor.moveToNext()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(2)
            assertThat(cursor.getString(1)).isEqualTo("indefinite")
            assertThat(cursor.isNull(2)).isTrue()
        }
        db.close()
        context.deleteDatabase(MIGRATION_TEST_DB)
    }

    @Test
    fun `update rewrites stored alarm and refreshes next trigger metadata`() = runTest {
        val created = repository.create(
            AlarmDraft(
                title = "Take medicine",
                hour = 8,
                minute = 30,
                repeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 24)),
                enabled = true,
                ringtoneUri = "content://settings/system/alarm_alert",
                source = AlarmSource.MANUAL,
                aiOriginalText = null,
            ),
        )
        clock.set(ZonedDateTime.of(2026, 4, 23, 10, 0, 0, 0, zone).toInstant())

        val updated = repository.update(
            created.copy(
                title = "Morning workout",
                hour = 7,
                minute = 45,
                repeatRule = RepeatRule.Daily,
                enabled = true,
                ringtoneUri = "content://settings/system/alarm_alert_2",
                source = AlarmSource.AI,
                aiOriginalText = "every morning at 7:45",
            ),
        )

        assertThat(updated.id).isEqualTo(created.id)
        assertThat(updated.title).isEqualTo("Morning workout")
        assertThat(updated.repeatRule).isEqualTo(RepeatRule.Daily)
        assertThat(updated.source).isEqualTo(AlarmSource.AI)
        assertThat(updated.aiOriginalText).isEqualTo("every morning at 7:45")
        assertThat(updated.nextTriggerAtMillis).isEqualTo(
            ZonedDateTime.of(2026, 4, 24, 7, 45, 0, 0, zone).toInstant().toEpochMilli(),
        )
        assertThat(updated.createdAtMillis).isEqualTo(created.createdAtMillis)
        assertThat(updated.updatedAtMillis).isEqualTo(clock.instant().toEpochMilli())
        assertThat(repository.get(created.id)).isEqualTo(updated)
    }

    @Test
    fun `generic update clears pause state when enabling paused alarm`() = runTest {
        val created = repository.create(dailyAlarmDraft(enabled = true))
        val paused = repository.updateFromManagement(
            created.copy(
                enabled = false,
                nextTriggerAtMillis = null,
                pauseMode = AlarmPauseMode.INDEFINITE,
                pausedOccurrenceAtMillis = null,
            ),
        )
        clock.set(ZonedDateTime.of(2026, 4, 23, 12, 0, 0, 0, zone).toInstant())

        val updated = repository.update(
            paused.copy(
                enabled = true,
                pauseMode = AlarmPauseMode.INDEFINITE,
                pausedOccurrenceAtMillis = 1_776_798_000_000,
            ),
        )

        assertThat(updated.enabled).isTrue()
        assertThat(updated.pauseMode).isEqualTo(AlarmPauseMode.NONE)
        assertThat(updated.pausedOccurrenceAtMillis).isNull()
        assertThat(updated.nextTriggerAtMillis).isEqualTo(
            ZonedDateTime.of(2026, 4, 24, 8, 0, 0, 0, zone).toInstant().toEpochMilli(),
        )
    }

    @Test
    fun `disable clears next trigger but keeps alarm`() = runTest {
        val created = repository.create(
            dailyAlarmDraft(enabled = true),
        )
        clock.set(ZonedDateTime.of(2026, 4, 23, 12, 0, 0, 0, zone).toInstant())

        val disabled = repository.disable(created.id)

        assertThat(disabled).isNotNull()
        assertThat(disabled!!.enabled).isFalse()
        assertThat(disabled.nextTriggerAtMillis).isNull()
        assertThat(disabled.updatedAtMillis).isEqualTo(clock.instant().toEpochMilli())
        assertThat(repository.get(created.id)).isEqualTo(disabled)
    }

    @Test
    fun `enable recalculates next trigger for disabled alarm`() = runTest {
        val created = repository.create(
            dailyAlarmDraft(enabled = false),
        )
        clock.set(ZonedDateTime.of(2026, 4, 23, 12, 0, 0, 0, zone).toInstant())

        val enabled = repository.enable(created.id)

        assertThat(enabled).isNotNull()
        assertThat(enabled!!.enabled).isTrue()
        assertThat(enabled.nextTriggerAtMillis).isEqualTo(
            ZonedDateTime.of(2026, 4, 24, 8, 0, 0, 0, zone).toInstant().toEpochMilli(),
        )
        assertThat(enabled.updatedAtMillis).isEqualTo(clock.instant().toEpochMilli())
        assertThat(repository.get(created.id)).isEqualTo(enabled)
    }

    @Test
    fun `delete removes repeating alarm from repository`() = runTest {
        val created = repository.create(
            dailyAlarmDraft(enabled = true),
        )

        repository.delete(created.id)

        assertThat(repository.get(created.id)).isNull()
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `delete once alarm removes it from repository`() = runTest {
        val alarm = repository.create(
            AlarmDraft(
                title = "One shot",
                hour = 8,
                minute = 0,
                repeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 24)),
                enabled = true,
                ringtoneUri = "content://settings/system/alarm_alert",
                source = AlarmSource.MANUAL,
                aiOriginalText = null,
            ),
        )

        repository.delete(alarm.id)

        assertThat(repository.get(alarm.id)).isNull()
    }

    @Test
    fun `create rejects invalid hour before persisting`() = runTest {
        val error = runCatching {
            repository.create(
                dailyAlarmDraft(
                    enabled = true,
                    hour = 24,
                ),
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("INVALID_HOUR")
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `create rejects invalid minute before persisting`() = runTest {
        val error = runCatching {
            repository.create(
                dailyAlarmDraft(
                    enabled = true,
                    minute = 60,
                ),
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("INVALID_MINUTE")
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `create rejects empty custom weekdays before persisting unreadable row`() = runTest {
        val error = runCatching {
            repository.create(
                dailyAlarmDraft(
                    enabled = true,
                    repeatRule = RepeatRule.CustomWeekdays(emptySet()),
                ),
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("EMPTY_CUSTOM_WEEKDAYS")
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `create rejects expired enabled one time alarm before persisting`() = runTest {
        val error = runCatching {
            repository.create(
                dailyAlarmDraft(
                    enabled = true,
                    repeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 23)),
                    hour = 8,
                    minute = 0,
                ),
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("EXPIRED_ONE_TIME_ALARM")
        assertThat(repository.alarms.first()).isEmpty()
    }

    @Test
    fun `update rejects invalid hour and keeps stored alarm unchanged`() = runTest {
        val created = repository.create(dailyAlarmDraft(enabled = true))

        val error = runCatching {
            repository.update(created.copy(hour = -1))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("INVALID_HOUR")
        assertThat(repository.get(created.id)).isEqualTo(created)
    }

    @Test
    fun `update rejects invalid minute and keeps stored alarm unchanged`() = runTest {
        val created = repository.create(dailyAlarmDraft(enabled = true))

        val error = runCatching {
            repository.update(created.copy(minute = 60))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("INVALID_MINUTE")
        assertThat(repository.get(created.id)).isEqualTo(created)
    }

    @Test
    fun `update rejects empty custom weekdays and keeps stored alarm unchanged`() = runTest {
        val created = repository.create(dailyAlarmDraft(enabled = true))

        val error = runCatching {
            repository.update(created.copy(repeatRule = RepeatRule.CustomWeekdays(emptySet())))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("EMPTY_CUSTOM_WEEKDAYS")
        assertThat(repository.get(created.id)).isEqualTo(created)
    }

    @Test
    fun `update rejects expired enabled one time alarm and keeps stored alarm unchanged`() = runTest {
        val created = repository.create(dailyAlarmDraft(enabled = true))

        val error = runCatching {
            repository.update(
                created.copy(
                    repeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 23)),
                    hour = 8,
                    minute = 0,
                    enabled = true,
                ),
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("EXPIRED_ONE_TIME_ALARM")
        assertThat(repository.get(created.id)).isEqualTo(created)
    }

    @Test
    fun `create computes next trigger with current zone provider instead of repository construction zone`() = runTest {
        clock.set(Instant.parse("2026-04-23T00:30:00Z"))
        currentZoneId = losAngelesZone

        val created = repository.create(
            dailyAlarmDraft(
                enabled = true,
                hour = 8,
                minute = 0,
            ),
        )

        val expected = ZonedDateTime.of(
            clock.instant().atZone(losAngelesZone).toLocalDate().plusDays(1),
            LocalTime.of(8, 0),
            losAngelesZone,
        ).toInstant().toEpochMilli()

        assertThat(created.nextTriggerAtMillis).isEqualTo(expected)
    }

    private fun dailyAlarmDraft(
        enabled: Boolean,
        hour: Int = 8,
        minute: Int = 0,
        repeatRule: RepeatRule = RepeatRule.Daily,
    ) = AlarmDraft(
        title = "Daily reminder",
        hour = hour,
        minute = minute,
        repeatRule = repeatRule,
        enabled = enabled,
        ringtoneUri = "content://settings/system/alarm_alert",
        source = AlarmSource.MANUAL,
        aiOriginalText = null,
    )

    private class MutableClock(
        private var currentInstant: Instant,
        private val zoneId: ZoneId,
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId

        override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

        override fun instant(): Instant = currentInstant

        fun set(newInstant: Instant) {
            currentInstant = newInstant
        }
    }

    private fun openHelper(
        version: Int,
        onCreate: (SupportSQLiteDatabase) -> Unit,
        onUpgrade: (SupportSQLiteDatabase, Int, Int) -> Unit = { _, _, _ -> },
    ): SupportSQLiteOpenHelper {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(MIGRATION_TEST_DB)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = onUpgrade(db, oldVersion, newVersion)
                    },
                )
                .build(),
        )
    }

    private fun createVersion2Database(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `alarms` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `hour` INTEGER NOT NULL,
                `minute` INTEGER NOT NULL,
                `repeatType` TEXT NOT NULL,
                `daysOfWeekCsv` TEXT NOT NULL,
                `onceDate` TEXT,
                `startDate` TEXT,
                `endDate` TEXT,
                `intervalWeeks` INTEGER,
                `enabled` INTEGER NOT NULL,
                `ringtoneUri` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `aiOriginalText` TEXT,
                `nextTriggerAtMillis` INTEGER,
                `createdAtMillis` INTEGER NOT NULL,
                `updatedAtMillis` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO alarms (
                id, title, hour, minute, repeatType, daysOfWeekCsv, onceDate,
                startDate, endDate, intervalWeeks, enabled, ringtoneUri, source,
                aiOriginalText, nextTriggerAtMillis, createdAtMillis, updatedAtMillis
            ) VALUES (
                1, 'Enabled', 8, 0, 'daily', '', NULL,
                NULL, NULL, NULL, 1, 'content://settings/system/alarm_alert', 'manual',
                NULL, 1234, 1000, 1000
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO alarms (
                id, title, hour, minute, repeatType, daysOfWeekCsv, onceDate,
                startDate, endDate, intervalWeeks, enabled, ringtoneUri, source,
                aiOriginalText, nextTriggerAtMillis, createdAtMillis, updatedAtMillis
            ) VALUES (
                2, 'Disabled', 9, 0, 'daily', '', NULL,
                NULL, NULL, NULL, 0, 'content://settings/system/alarm_alert', 'manual',
                NULL, NULL, 1000, 1000
            )
            """.trimIndent(),
        )
    }

    private companion object {
        const val MIGRATION_TEST_DB = "room-alarm-repository-migration"
    }
}
