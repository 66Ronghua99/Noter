package com.cory.noter.data.alarm

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AlarmCalendarEventDao {
    @Insert
    suspend fun insert(mapping: AlarmCalendarEventEntity)

    @Query("SELECT * FROM alarm_calendar_events WHERE alarmId = :alarmId")
    suspend fun getByAlarmId(alarmId: Long): AlarmCalendarEventEntity?

    @Query("DELETE FROM alarm_calendar_events WHERE alarmId = :alarmId")
    suspend fun deleteByAlarmId(alarmId: Long)
}
