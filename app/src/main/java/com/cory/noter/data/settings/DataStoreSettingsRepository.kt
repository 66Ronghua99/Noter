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
        AppSettings(
            openRouterApiKey = preferences[OPEN_ROUTER_API_KEY] ?: "",
            selectedModelId = storedModelId?.also(::requireKnownModelId) ?: OpenRouterModel.DefaultId,
            selectedAsrModelId = storedAsrModelId?.also(::requireKnownAsrModelId) ?: AsrModel.DefaultId,
            defaultRingtoneUri = preferences[DEFAULT_RINGTONE_URI] ?: AppSettings.DefaultRingtoneUri,
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

    private companion object {
        val OPEN_ROUTER_API_KEY = stringPreferencesKey("open_router_api_key")
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val SELECTED_ASR_MODEL_ID = stringPreferencesKey("selected_asr_model_id")
        val DEFAULT_RINGTONE_URI = stringPreferencesKey("default_ringtone_uri")
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
}
