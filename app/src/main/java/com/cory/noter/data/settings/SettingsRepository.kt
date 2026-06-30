package com.cory.noter.data.settings

import com.cory.noter.domain.settings.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setOpenRouterApiKey(apiKey: String): Result<Unit>

    suspend fun setSelectedModel(modelId: String): Result<Unit>

    suspend fun setSelectedAsrModel(modelId: String): Result<Unit>

    suspend fun setDefaultRingtoneUri(ringtoneUri: String): Result<Unit>

    suspend fun setThemePreset(presetId: String): Result<Unit>

    suspend fun setCustomThemeSeedColor(seedColor: String): Result<Unit>
}
