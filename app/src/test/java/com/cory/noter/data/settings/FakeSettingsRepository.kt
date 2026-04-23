package com.cory.noter.data.settings

import com.cory.noter.ai.OpenRouterModel
import com.cory.noter.domain.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(
    initialSettings: AppSettings = AppSettings(
        openRouterApiKey = "",
        selectedModelId = OpenRouterModel.DefaultId,
        defaultRingtoneUri = "",
    ),
) : SettingsRepository {
    private val state = MutableStateFlow(initialSettings)

    override val settings: Flow<AppSettings> = state

    override suspend fun setOpenRouterApiKey(apiKey: String) {
        state.update { it.copy(openRouterApiKey = apiKey) }
    }

    override suspend fun setSelectedModel(modelId: String): Result<Unit> {
        if (modelId !in OpenRouterModel.builtInIds) {
            return Result.failure(
                IllegalArgumentException("UNKNOWN_MODEL_ID: $modelId"),
            )
        }

        state.update { it.copy(selectedModelId = modelId) }
        return Result.success(Unit)
    }

    override suspend fun setDefaultRingtoneUri(ringtoneUri: String) {
        state.update { it.copy(defaultRingtoneUri = ringtoneUri) }
    }

    suspend fun set(settings: AppSettings) {
        state.emit(settings)
    }
}
