package com.cory.noter.data.alarm

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.RepeatRule
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlarmCalendarEventRepositoryTest {
    private lateinit var database: AlarmDatabase
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var mappingRepository: AlarmCalendarEventRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AlarmDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        alarmRepository = RoomAlarmRepository(
            alarmDao = database.alarmDao(),
            clock = Clock.fixed(Instant.parse("2026-04-23T01:00:00Z"), ZoneId.of("UTC")),
            zoneIdProvider = { ZoneId.of("UTC") },
        )
        mappingRepository = RoomAlarmCalendarEventRepository(database.alarmCalendarEventDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `migration 3 to 4 creates alarm calendar events table`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(MIGRATION_TEST_DB)
        openHelper(version = 3, onCreate = ::createVersion3Database).apply {
            writableDatabase.close()
            close()
        }

        val db = openHelper(
            version = 4,
            onCreate = {},
            onUpgrade = { database, oldVersion, newVersion ->
                check(oldVersion == 3 && newVersion == 4)
                AlarmDatabase.MIGRATION_3_4.migrate(database)
            },
        ).writableDatabase

        val columns = tableColumns(db, "alarm_calendar_events")
        assertThat(columns).containsAtLeast(
            "alarmId",
            "calendarId",
            "eventId",
            "durationMinutes",
            "reason",
            "status",
            "createdAtMillis",
            "updatedAtMillis",
        )
        val foreignKeys = foreignKeyTables(db, "alarm_calendar_events")
        assertThat(foreignKeys).contains("alarms")

        db.close()
        context.deleteDatabase(MIGRATION_TEST_DB)
    }

    @Test
    fun `insert and load mapping by alarm id`() = runTest {
        val alarm = alarmRepository.create(alarmDraft())
        val mapping = mapping(alarmId = alarm.id)

        mappingRepository.insert(mapping)

        assertThat(mappingRepository.getByAlarmId(alarm.id)).isEqualTo(mapping)
    }

    @Test
    fun `duplicate mapping for the same alarm is rejected`() = runTest {
        val alarm = alarmRepository.create(alarmDraft())
        mappingRepository.insert(mapping(alarmId = alarm.id, eventId = 100L))

        val error = runCatching {
            mappingRepository.insert(mapping(alarmId = alarm.id, eventId = 101L))
        }.exceptionOrNull()

        assertThat(error).isNotNull()
    }

    @Test
    fun `mapping for missing alarm is rejected`() = runTest {
        val error = runCatching {
            mappingRepository.insert(mapping(alarmId = 404L))
        }.exceptionOrNull()

        assertThat(error).isNotNull()
    }

    @Test
    fun `deleting alarm cascades calendar mapping`() = runTest {
        val alarm = alarmRepository.create(alarmDraft())
        mappingRepository.insert(mapping(alarmId = alarm.id))

        alarmRepository.delete(alarm.id)

        assertThat(mappingRepository.getByAlarmId(alarm.id)).isNull()
    }

    private fun alarmDraft(): AlarmDraft = AlarmDraft(
        title = "Doctor appointment",
        hour = 9,
        minute = 30,
        repeatRule = RepeatRule.Once(LocalDate.of(2026, 4, 24)),
        enabled = true,
        ringtoneUri = "content://settings/system/alarm_alert",
        source = AlarmSource.AI,
        aiOriginalText = "add my doctor appointment tomorrow at 9:30 to calendar",
    )

    private fun mapping(
        alarmId: Long,
        eventId: Long = 100L,
    ): AlarmCalendarEventMapping = AlarmCalendarEventMapping(
        alarmId = alarmId,
        calendarId = 42L,
        eventId = eventId,
        durationMinutes = 30,
        reason = "explicit_user_request",
        status = "synced",
        createdAtMillis = 1_000L,
        updatedAtMillis = 1_000L,
    )

    private fun tableColumns(
        db: SupportSQLiteDatabase,
        tableName: String,
    ): List<String> {
        db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val names = mutableListOf<String>()
            while (cursor.moveToNext()) {
                names += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            return names
        }
    }

    private fun foreignKeyTables(
        db: SupportSQLiteDatabase,
        tableName: String,
    ): List<String> {
        db.query("PRAGMA foreign_key_list(`$tableName`)").use { cursor ->
            val tables = mutableListOf<String>()
            while (cursor.moveToNext()) {
                tables += cursor.getString(cursor.getColumnIndexOrThrow("table"))
            }
            return tables
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

    private fun createVersion3Database(db: SupportSQLiteDatabase) {
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
                `updatedAtMillis` INTEGER NOT NULL,
                `pauseMode` TEXT NOT NULL DEFAULT 'none',
                `pausedOccurrenceAtMillis` INTEGER
            )
            """.trimIndent(),
        )
    }

    private companion object {
        const val MIGRATION_TEST_DB = "alarm-calendar-event-migration"
    }
}
