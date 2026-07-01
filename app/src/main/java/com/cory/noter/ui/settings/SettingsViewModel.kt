package com.cory.noter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.cory.noter.R
import com.cory.noter.ai.AsrModel
import com.cory.noter.ai.OpenRouterModel
import com.cory.noter.data.settings.SettingsRepository
import com.cory.noter.domain.settings.AppSettings
import com.cory.noter.permissions.PermissionStatusReader
import com.cory.noter.ui.text.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionGuidanceUiModel(
    val id: String,
    @param:StringRes val titleResId: Int,
    val granted: Boolean,
    @param:StringRes val summaryResId: Int,
    @param:StringRes val actionLabelResId: Int?,
)

data class SettingsDirectoryRowUiModel(
    val id: String,
    @param:StringRes val titleResId: Int,
    val summary: UiText,
)

data class SettingsUiState(
    val openRouterApiKey: String = "",
    val selectedModelId: String = OpenRouterModel.DefaultId,
    val selectedAsrModelId: String = AsrModel.DefaultId,
    val defaultRingtoneUri: String = "",
    val themePresetId: String = AppSettings.DefaultThemePresetId,
    val customThemeSeedColor: String? = null,
    val customThemeSeedColorInput: String = "",
    val modelOptions: List<String> = OpenRouterModel.builtInIds,
    val asrModelOptions: List<String> = AsrModel.builtInIds,
    val themePresetOptions: List<String> = AppSettings.BuiltInThemePresetIds.toList(),
    val directoryRows: List<SettingsDirectoryRowUiModel> = emptyList(),
    val permissionRows: List<PermissionGuidanceUiModel> = emptyList(),
    val errorMessage: UiText? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val exactAlarmPermissionReader: PermissionStatusReader,
    private val notificationPermissionProvider: () -> Boolean,
    private val batteryOptimizationIgnoredProvider: () -> Boolean,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        SettingsUiState(permissionRows = emptyList()),
    )
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

    init {
        refreshPermissionRows()
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                mutableUiState.update { current ->
                    val updated = current.copy(
                        openRouterApiKey = settings.openRouterApiKey,
                        selectedModelId = settings.selectedModelId,
                        selectedAsrModelId = settings.selectedAsrModelId,
                        defaultRingtoneUri = settings.defaultRingtoneUri,
                        themePresetId = settings.themePresetId,
                        customThemeSeedColor = settings.customThemeSeedColor,
                        customThemeSeedColorInput = settings.customThemeSeedColor
                            ?: current.customThemeSeedColorInput,
                    )
                    updated.copy(directoryRows = buildDirectoryRows(updated))
                }
            }
        }
    }

    fun refreshPermissionRows() {
        mutableUiState.update { current ->
            val updated = current.copy(
                permissionRows = buildPermissionRows(),
            )
            updated.copy(directoryRows = buildDirectoryRows(updated))
        }
    }

    fun onApiKeyChanged(apiKey: String) {
        mutableUiState.update {
            it.copy(
                openRouterApiKey = apiKey,
                errorMessage = null,
            )
        }
    }

    fun saveApiKey() {
        viewModelScope.launch {
            val result = settingsRepository.setOpenRouterApiKey(uiState.value.openRouterApiKey)
            mutableUiState.update {
                it.copy(errorMessage = result.exceptionOrNull()?.message?.let(UiText::Raw))
            }
        }
    }

    fun onModelSelected(modelId: String) {
        viewModelScope.launch {
            val result = settingsRepository.setSelectedModel(modelId)
            mutableUiState.update {
                it.copy(errorMessage = result.exceptionOrNull()?.message?.let(UiText::Raw))
            }
        }
    }

    fun onAsrModelSelected(modelId: String) {
        viewModelScope.launch {
            val result = settingsRepository.setSelectedAsrModel(modelId)
            mutableUiState.update {
                it.copy(errorMessage = result.exceptionOrNull()?.message?.let(UiText::Raw))
            }
        }
    }

    fun onDefaultRingtoneSelected(ringtoneUri: String) {
        viewModelScope.launch {
            val result = settingsRepository.setDefaultRingtoneUri(ringtoneUri)
            mutableUiState.update {
                it.copy(errorMessage = result.exceptionOrNull()?.message?.let(UiText::Raw))
            }
        }
    }

    fun onThemePresetSelected(presetId: String) {
        viewModelScope.launch {
            val result = settingsRepository.setThemePreset(presetId)
            mutableUiState.update {
                it.copy(errorMessage = result.exceptionOrNull()?.message?.let(UiText::Raw))
            }
        }
    }

    fun onCustomThemeSeedColorChanged(seedColor: String) {
        mutableUiState.update {
            it.copy(
                customThemeSeedColorInput = seedColor,
                errorMessage = null,
            )
        }
    }

    fun saveCustomThemeSeedColor() {
        viewModelScope.launch {
            val result = settingsRepository.setCustomThemeSeedColor(
                uiState.value.customThemeSeedColorInput,
            )
            mutableUiState.update {
                it.copy(errorMessage = result.exceptionOrNull()?.message?.let(UiText::Raw))
            }
        }
    }

    private fun buildDirectoryRows(state: SettingsUiState): List<SettingsDirectoryRowUiModel> =
        listOf(
            SettingsDirectoryRowUiModel(
                id = "appearance",
                titleResId = R.string.settings_directory_appearance,
                summary = appearanceSummary(state),
            ),
            SettingsDirectoryRowUiModel(
                id = "ai_voice",
                titleResId = R.string.settings_directory_ai_voice,
                summary = UiText.Resource(
                    R.string.settings_summary_ai_voice,
                    listOf(state.selectedModelId),
                ),
            ),
            SettingsDirectoryRowUiModel(
                id = "sound",
                titleResId = R.string.settings_directory_sound,
                summary = UiText.Resource(
                    R.string.settings_summary_sound,
                    listOf(state.defaultRingtoneUri),
                ),
            ),
            SettingsDirectoryRowUiModel(
                id = "permissions",
                titleResId = R.string.settings_directory_permissions,
                summary = permissionsSummary(state.permissionRows),
            ),
        )

    private fun appearanceSummary(state: SettingsUiState): UiText = when (state.themePresetId) {
        AppSettings.CustomThemePresetId -> UiText.Resource(R.string.settings_theme_preset_custom)
        "calm_blue" -> UiText.Resource(R.string.settings_theme_preset_calm_blue)
        "fresh_green" -> UiText.Resource(R.string.settings_theme_preset_fresh_green)
        "soft_rose" -> UiText.Resource(R.string.settings_theme_preset_soft_rose)
        "neutral_gray" -> UiText.Resource(R.string.settings_theme_preset_neutral_gray)
        else -> UiText.Resource(R.string.settings_theme_preset_calm_blue)
    }

    private fun permissionsSummary(rows: List<PermissionGuidanceUiModel>): UiText {
        val deniedCount = rows.count { !it.granted }
        return if (deniedCount == 0) {
            UiText.Resource(R.string.settings_summary_permissions_all_set)
        } else {
            UiText.Resource(R.string.settings_summary_permissions, listOf(deniedCount))
        }
    }

    private fun buildPermissionRows(): List<PermissionGuidanceUiModel> = listOf(
        PermissionGuidanceUiModel(
            id = "notifications",
            titleResId = R.string.settings_permission_notifications_title,
            granted = notificationPermissionProvider(),
            summaryResId = R.string.settings_permission_notifications_summary,
            actionLabelResId = R.string.settings_permission_notifications_action,
        ),
        PermissionGuidanceUiModel(
            id = "exact_alarms",
            titleResId = R.string.settings_permission_exact_alarms_title,
            granted = exactAlarmPermissionReader.canScheduleExactAlarms(),
            summaryResId = R.string.settings_permission_exact_alarms_summary,
            actionLabelResId = R.string.settings_permission_exact_alarms_action,
        ),
        PermissionGuidanceUiModel(
            id = "battery_optimization",
            titleResId = R.string.settings_permission_battery_title,
            granted = batteryOptimizationIgnoredProvider(),
            summaryResId = R.string.settings_permission_battery_summary,
            actionLabelResId = R.string.settings_permission_battery_action,
        ),
    )
}
