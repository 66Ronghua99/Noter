package com.cory.noter.data.settings

import com.cory.noter.ai.AsrModel
import com.cory.noter.ai.OpenRouterModel
import com.cory.noter.domain.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(
    initialSettings: AppSettings = AppSettings(
        openRouterApiKey = "",
        selectedModelId = OpenRouterModel.DefaultId,
        selectedAsrModelId = AsrModel.DefaultId,
        defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
    ),
) : SettingsRepository {
    private val state = MutableStateFlow(initialSettings)

    override val settings: Flow<AppSettings> = state

    override suspend fun setOpenRouterApiKey(apiKey: String): Result<Unit> {
        state.update { it.copy(openRouterApiKey = apiKey) }
        return Result.success(Unit)
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

    override suspend fun setSelectedAsrModel(modelId: String): Result<Unit> {
        if (modelId !in AsrModel.builtInIds) {
            return Result.failure(
                IllegalArgumentException("UNKNOWN_ASR_MODEL_ID: $modelId"),
            )
        }

        state.update { it.copy(selectedAsrModelId = modelId) }
        return Result.success(Unit)
    }

    override suspend fun setDefaultRingtoneUri(ringtoneUri: String): Result<Unit> {
        state.update { it.copy(defaultRingtoneUri = ringtoneUri) }
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
