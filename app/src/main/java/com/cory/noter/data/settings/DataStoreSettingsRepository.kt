package com.cory.noter.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cory.noter.ai.OpenRouterModel
import com.cory.noter.domain.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            openRouterApiKey = preferences[OPEN_ROUTER_API_KEY] ?: "",
            selectedModelId = preferences[SELECTED_MODEL_ID] ?: OpenRouterModel.DefaultId,
            defaultRingtoneUri = preferences[DEFAULT_RINGTONE_URI] ?: "",
        )
    }

    override suspend fun setOpenRouterApiKey(apiKey: String) {
        dataStore.edit { preferences ->
            preferences[OPEN_ROUTER_API_KEY] = apiKey
        }
    }

    override suspend fun setSelectedModel(modelId: String): Result<Unit> {
        if (modelId !in OpenRouterModel.builtInIds) {
            return Result.failure(
                IllegalArgumentException("UNKNOWN_MODEL_ID: $modelId"),
            )
        }

        dataStore.edit { preferences ->
            preferences[SELECTED_MODEL_ID] = modelId
        }
        return Result.success(Unit)
    }

    override suspend fun setDefaultRingtoneUri(ringtoneUri: String) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_RINGTONE_URI] = ringtoneUri
        }
    }

    private companion object {
        val OPEN_ROUTER_API_KEY = stringPreferencesKey("open_router_api_key")
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val DEFAULT_RINGTONE_URI = stringPreferencesKey("default_ringtone_uri")
    }
}
