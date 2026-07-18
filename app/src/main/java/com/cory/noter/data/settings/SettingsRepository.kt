package com.cory.noter.data.settings

import com.cory.noter.domain.settings.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    val themeSettings: Flow<AppSettings>

    suspend fun setDefaultRingtoneUri(ringtoneUri: String): Result<Unit>

    suspend fun setDefaultCalendarId(calendarId: Long): Result<Unit>

    suspend fun clearDefaultCalendarId(): Result<Unit>

    suspend fun setThemePreset(presetId: String): Result<Unit>

    suspend fun setCustomThemeSeedColor(seedColor: String): Result<Unit>
}
