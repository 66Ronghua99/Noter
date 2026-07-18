package com.cory.noter.data.settings

import com.cory.noter.domain.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(
    initialSettings: AppSettings = AppSettings(
        defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
        defaultCalendarId = null,
    ),
) : SettingsRepository {
    private val state = MutableStateFlow(initialSettings)

    override val settings: Flow<AppSettings> = state
    override val themeSettings: Flow<AppSettings> = state

    override suspend fun setDefaultRingtoneUri(ringtoneUri: String): Result<Unit> {
        state.update { it.copy(defaultRingtoneUri = ringtoneUri) }
        return Result.success(Unit)
    }

    override suspend fun setDefaultCalendarId(calendarId: Long): Result<Unit> {
        state.update { it.copy(defaultCalendarId = calendarId) }
        return Result.success(Unit)
    }

    override suspend fun clearDefaultCalendarId(): Result<Unit> {
        state.update { it.copy(defaultCalendarId = null) }
        return Result.success(Unit)
    }

    override suspend fun setThemePreset(presetId: String): Result<Unit> {
        if (presetId !in AppSettings.BuiltInThemePresetIds) {
            return Result.failure(
                IllegalArgumentException("UNKNOWN_THEME_PRESET_ID: $presetId"),
            )
        }

        state.update {
            it.copy(
                themePresetId = presetId,
                customThemeSeedColor = null,
            )
        }
        return Result.success(Unit)
    }

    override suspend fun setCustomThemeSeedColor(seedColor: String): Result<Unit> {
        if (!AppSettings.isValidThemeSeedColor(seedColor)) {
            return Result.failure(
                IllegalArgumentException("INVALID_THEME_SEED_COLOR: $seedColor"),
            )
        }

        state.update {
            it.copy(
                themePresetId = AppSettings.CustomThemePresetId,
                customThemeSeedColor = seedColor.lowercase(),
            )
        }
        return Result.success(Unit)
    }

    suspend fun set(settings: AppSettings) {
        state.emit(settings)
    }
}
