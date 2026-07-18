package com.cory.noter.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cory.noter.domain.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    private val migratedData: Flow<Preferences> = flow {
        dataStore.edit { preferences ->
            preferences.remove(OPEN_ROUTER_API_KEY)
            preferences.remove(SELECTED_MODEL_ID)
            preferences.remove(SELECTED_ASR_MODEL_ID)
        }
        emitAll(dataStore.data)
    }

    override val settings: Flow<AppSettings> = migratedData.map { preferences ->
        val themeSettings = readThemeSettings(preferences)
        AppSettings(
            defaultRingtoneUri = preferences[DEFAULT_RINGTONE_URI] ?: AppSettings.DefaultRingtoneUri,
            defaultCalendarId = preferences[DEFAULT_CALENDAR_ID],
            themePresetId = themeSettings.presetId,
            customThemeSeedColor = themeSettings.customSeedColor,
        )
    }

    override val themeSettings: Flow<AppSettings> = migratedData.map { preferences ->
        readThemeSettings(preferences).toAppSettings()
    }

    override suspend fun setDefaultRingtoneUri(ringtoneUri: String): Result<Unit> = runCatching {
        dataStore.edit { preferences ->
            preferences[DEFAULT_RINGTONE_URI] = ringtoneUri
        }
        Unit
    }

    override suspend fun setDefaultCalendarId(calendarId: Long): Result<Unit> = runCatching {
        dataStore.edit { preferences ->
            preferences[DEFAULT_CALENDAR_ID] = calendarId
        }
        Unit
    }

    override suspend fun clearDefaultCalendarId(): Result<Unit> = runCatching {
        dataStore.edit { preferences ->
            preferences.remove(DEFAULT_CALENDAR_ID)
        }
        Unit
    }

    override suspend fun setThemePreset(presetId: String): Result<Unit> = runCatching {
        requireKnownThemePresetId(presetId)
        dataStore.edit { preferences ->
            preferences[THEME_PRESET_ID] = presetId
            preferences.remove(CUSTOM_THEME_SEED_COLOR)
        }
        Unit
    }

    override suspend fun setCustomThemeSeedColor(seedColor: String): Result<Unit> = runCatching {
        requireValidCustomThemeSeedColor(seedColor)
        dataStore.edit { preferences ->
            preferences[THEME_PRESET_ID] = AppSettings.CustomThemePresetId
            preferences[CUSTOM_THEME_SEED_COLOR] = seedColor.lowercase()
        }
        Unit
    }

    private companion object {
        val OPEN_ROUTER_API_KEY = stringPreferencesKey("open_router_api_key")
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val SELECTED_ASR_MODEL_ID = stringPreferencesKey("selected_asr_model_id")
        val DEFAULT_RINGTONE_URI = stringPreferencesKey("default_ringtone_uri")
        val DEFAULT_CALENDAR_ID = longPreferencesKey("default_calendar_id")
        val THEME_PRESET_ID = stringPreferencesKey("theme_preset_id")
        val CUSTOM_THEME_SEED_COLOR = stringPreferencesKey("custom_theme_seed_color")
    }

    private fun requireKnownThemePresetId(presetId: String) {
        require(presetId in AppSettings.BuiltInThemePresetIds) {
            "UNKNOWN_THEME_PRESET_ID: $presetId"
        }
    }

    private fun requireValidCustomThemeSeedColor(seedColor: String) {
        require(AppSettings.isValidThemeSeedColor(seedColor)) {
            "INVALID_THEME_SEED_COLOR: $seedColor"
        }
    }

    private fun readThemeSettings(preferences: Preferences): ThemeSettings {
        val storedPresetId = preferences[THEME_PRESET_ID] ?: AppSettings.DefaultThemePresetId
        if (storedPresetId == AppSettings.CustomThemePresetId) {
            val customSeedColor = preferences[CUSTOM_THEME_SEED_COLOR]
            return if (customSeedColor != null && AppSettings.isValidThemeSeedColor(customSeedColor)) {
                ThemeSettings(
                    presetId = AppSettings.CustomThemePresetId,
                    customSeedColor = customSeedColor.lowercase(),
                )
            } else {
                ThemeSettings()
            }
        }

        return if (storedPresetId in AppSettings.BuiltInThemePresetIds) {
            ThemeSettings(presetId = storedPresetId)
        } else {
            ThemeSettings()
        }
    }

    private data class ThemeSettings(
        val presetId: String = AppSettings.DefaultThemePresetId,
        val customSeedColor: String? = null,
    ) {
        fun toAppSettings(): AppSettings = AppSettings(
            defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
            defaultCalendarId = null,
            themePresetId = presetId,
            customThemeSeedColor = customSeedColor,
        )
    }
}
