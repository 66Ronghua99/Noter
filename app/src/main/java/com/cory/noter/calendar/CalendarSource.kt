package com.cory.noter.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val writable: Boolean,
)

sealed interface CalendarSourceResult {
    data object MissingPermission : CalendarSourceResult
    data class Available(val calendars: List<DeviceCalendar>) : CalendarSourceResult
}

fun interface CalendarSource {
    suspend fun loadCalendars(): CalendarSourceResult
}

class AndroidCalendarSource(
    private val context: Context,
) : CalendarSource {
    override suspend fun loadCalendars(): CalendarSourceResult = withContext(Dispatchers.IO) {
        if (!hasCalendarPermissions()) {
            return@withContext CalendarSourceResult.MissingPermission
        }

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )

        val calendars = mutableListOf<DeviceCalendar>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val displayNameIndex = cursor.getColumnIndexOrThrow(
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            )
            val accountNameIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val accountTypeIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
            val accessLevelIndex = cursor.getColumnIndexOrThrow(
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            )

            while (cursor.moveToNext()) {
                val accessLevel = cursor.getInt(accessLevelIndex)
                calendars += DeviceCalendar(
                    id = cursor.getLong(idIndex),
                    displayName = cursor.getString(displayNameIndex).orEmpty(),
                    accountName = cursor.getString(accountNameIndex).orEmpty(),
                    accountType = cursor.getString(accountTypeIndex).orEmpty(),
                    writable = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                )
            }
        }

        CalendarSourceResult.Available(calendars)
    }

    private fun hasCalendarPermissions(): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }
}
