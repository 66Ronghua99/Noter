package com.cory.noter.data.alarm

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlarmEntity::class, AlarmCalendarEventEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun alarmCalendarEventDao(): AlarmCalendarEventDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN startDate TEXT")
                db.execSQL("ALTER TABLE alarms ADD COLUMN endDate TEXT")
                db.execSQL("ALTER TABLE alarms ADD COLUMN intervalWeeks INTEGER")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN pauseMode TEXT NOT NULL DEFAULT 'none'")
                db.execSQL("ALTER TABLE alarms ADD COLUMN pausedOccurrenceAtMillis INTEGER")
                db.execSQL("UPDATE alarms SET pauseMode = 'indefinite' WHERE enabled = 0")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `alarm_calendar_events` (
                        `alarmId` INTEGER NOT NULL,
                        `calendarId` INTEGER NOT NULL,
                        `eventId` INTEGER NOT NULL,
                        `durationMinutes` INTEGER NOT NULL,
                        `reason` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`alarmId`),
                        FOREIGN KEY(`alarmId`) REFERENCES `alarms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_alarm_calendar_events_alarmId` ON `alarm_calendar_events` (`alarmId`)",
                )
            }
        }
    }
}
