package com.cory.noter.permissions

import android.app.AlarmManager
import android.content.Context
import android.os.Build

fun interface PermissionStatusReader {
    fun canScheduleExactAlarms(): Boolean
}

class AndroidPermissionStatusReader(
    private val context: Context,
) : PermissionStatusReader {
    override fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
            ?: return false
        return alarmManager.canScheduleExactAlarms()
    }
}
