package com.cory.noter.data.alarm

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlarmEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN startDate TEXT")
                db.execSQL("ALTER TABLE alarms ADD COLUMN endDate TEXT")
                db.execSQL("ALTER TABLE alarms ADD COLUMN intervalWeeks INTEGER")
            }
        }
    }
}
