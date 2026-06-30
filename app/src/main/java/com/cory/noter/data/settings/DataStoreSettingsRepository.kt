package com.cory.noter.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cory.noter.ai.AsrModel
import com.cory.noter.ai.OpenRouterModel
import com.cory.noter.domain.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        val storedModelId = preferences[SELECTED_MODEL_ID]
        val storedAsrModelId = preferences[SELECTED_ASR_MODEL_ID]
        val themeSettings = readThemeSettings(preferences)
        AppSettings(
            openRouterApiKey = preferences[OPEN_ROUTER_API_KEY] ?: "",
            selectedModelId = storedModelId?.also(::requireKnownModelId) ?: OpenRouterModel.DefaultId,
            selectedAsrModelId = storedAsrModelId?.also(::requireKnownAsrModelId) ?: AsrModel.DefaultId,
            defaultRingtoneUri = preferences[DEFAULT_RINGTONE_URI] ?: AppSettings.DefaultRingtoneUri,
            themePresetId = themeSettings.presetId,
            customThemeSeedColor = themeSettings.customSeedColor,
        )
    }

    override suspend fun setOpenRouterApiKey(apiKey: String): Result<Unit> = runCatching {
        dataStore.edit { preferences ->
            preferences[OPEN_ROUTER_API_KEY] = apiKey
        }
        Unit
    }

    override suspend fun setSelectedModel(modelId: String): Result<Unit> = runCatching {
        requireKnownModelId(modelId)
        dataStore.edit { preferences ->
            preferences[SELECTED_MODEL_ID] = modelId
        }
        Unit
    }

    override suspend fun setSelectedAsrModel(modelId: String): Result<Unit> = runCatching {
        requireKnownAsrModelId(modelId)
        dataStore.edit { preferences ->
            preferences[SELECTED_ASR_MODEL_ID] = modelId
        }
        Unit
    }

    override suspend fun setDefaultRingtoneUri(ringtoneUri: String): Result<Unit> = runCatching {
        dataStore.edit { preferences ->
            preferences[DEFAULT_RINGTONE_URI] = ringtoneUri
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
        val THEME_PRESET_ID = stringPreferencesKey("theme_preset_id")
        val CUSTOM_THEME_SEED_COLOR = stringPreferencesKey("custom_theme_seed_color")
    }

    private fun requireKnownModelId(modelId: String) {
        require(modelId in OpenRouterModel.builtInIds) {
            "UNKNOWN_MODEL_ID: $modelId"
        }
    }

    private fun requireKnownAsrModelId(modelId: String) {
        require(modelId in AsrModel.builtInIds) {
            "UNKNOWN_ASR_MODEL_ID: $modelId"
        }
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
    )
}
