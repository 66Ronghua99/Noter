package com.cory.noter.data.alarm

data class AlarmCalendarEventMapping(
    val alarmId: Long,
    val calendarId: Long,
    val eventId: Long,
    val durationMinutes: Int,
    val reason: String,
    val status: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

interface AlarmCalendarEventRepository {
    suspend fun insert(mapping: AlarmCalendarEventMapping)
    suspend fun getByAlarmId(alarmId: Long): AlarmCalendarEventMapping?
    suspend fun deleteByAlarmId(alarmId: Long)
}

class RoomAlarmCalendarEventRepository(
    private val dao: AlarmCalendarEventDao,
) : AlarmCalendarEventRepository {
    override suspend fun insert(mapping: AlarmCalendarEventMapping) {
        dao.insert(mapping.toEntity())
    }

    override suspend fun getByAlarmId(alarmId: Long): AlarmCalendarEventMapping? =
        dao.getByAlarmId(alarmId)?.toDomain()

    override suspend fun deleteByAlarmId(alarmId: Long) {
        dao.deleteByAlarmId(alarmId)
    }

    private fun AlarmCalendarEventMapping.toEntity(): AlarmCalendarEventEntity =
        AlarmCalendarEventEntity(
            alarmId = alarmId,
            calendarId = calendarId,
            eventId = eventId,
            durationMinutes = durationMinutes,
            reason = reason,
            status = status,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
        )

    private fun AlarmCalendarEventEntity.toDomain(): AlarmCalendarEventMapping =
        AlarmCalendarEventMapping(
            alarmId = alarmId,
            calendarId = calendarId,
            eventId = eventId,
            durationMinutes = durationMinutes,
            reason = reason,
            status = status,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
        )
}
