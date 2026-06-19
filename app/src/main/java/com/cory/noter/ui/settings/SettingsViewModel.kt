package com.cory.noter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.cory.noter.R
import com.cory.noter.ai.OpenRouterModel
import com.cory.noter.data.settings.SettingsRepository
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

data class SettingsUiState(
    val openRouterApiKey: String = "",
    val selectedModelId: String = OpenRouterModel.DefaultId,
    val defaultRingtoneUri: String = "",
    val modelOptions: List<String> = OpenRouterModel.builtInIds,
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
                    current.copy(
                        openRouterApiKey = settings.openRouterApiKey,
                        selectedModelId = settings.selectedModelId,
                        defaultRingtoneUri = settings.defaultRingtoneUri,
                    )
                }
            }
        }
    }

    fun refreshPermissionRows() {
        mutableUiState.update { current ->
            current.copy(
                permissionRows = buildPermissionRows(),
            )
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

    fun onDefaultRingtoneSelected(ringtoneUri: String) {
        viewModelScope.launch {
            val result = settingsRepository.setDefaultRingtoneUri(ringtoneUri)
            mutableUiState.update {
                it.copy(errorMessage = result.exceptionOrNull()?.message?.let(UiText::Raw))
            }
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
