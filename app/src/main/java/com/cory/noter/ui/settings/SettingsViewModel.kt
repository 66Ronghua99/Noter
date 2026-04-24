package com.cory.noter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cory.noter.ai.OpenRouterModel
import com.cory.noter.data.settings.SettingsRepository
import com.cory.noter.permissions.PermissionStatusReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionGuidanceUiModel(
    val id: String,
    val title: String,
    val granted: Boolean,
    val summary: String,
)

data class SettingsUiState(
    val openRouterApiKey: String = "",
    val selectedModelId: String = OpenRouterModel.DefaultId,
    val defaultRingtoneUri: String = "",
    val modelOptions: List<String> = OpenRouterModel.builtInIds,
    val permissionRows: List<PermissionGuidanceUiModel> = emptyList(),
    val errorMessage: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    exactAlarmPermissionReader: PermissionStatusReader,
    notificationPermissionGranted: Boolean,
    batteryOptimizationIgnored: Boolean,
) : ViewModel() {
    private val permissionRows = listOf(
        PermissionGuidanceUiModel(
            id = "notifications",
            title = "Notifications",
            granted = notificationPermissionGranted,
            summary = "Needed for alarm alerts on Android 13 and later.",
        ),
        PermissionGuidanceUiModel(
            id = "exact_alarms",
            title = "Exact alarms",
            granted = exactAlarmPermissionReader.canScheduleExactAlarms(),
            summary = "Needed for reliable alarm delivery at the exact minute.",
        ),
        PermissionGuidanceUiModel(
            id = "battery_optimization",
            title = "Battery optimization",
            granted = batteryOptimizationIgnored,
            summary = "Helps the app keep alarms reliable in the background.",
        ),
    )

    private val mutableUiState = MutableStateFlow(
        SettingsUiState(permissionRows = permissionRows),
    )
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                mutableUiState.update { current ->
                    current.copy(
                        openRouterApiKey = settings.openRouterApiKey,
                        selectedModelId = settings.selectedModelId,
                        defaultRingtoneUri = settings.defaultRingtoneUri,
                        permissionRows = permissionRows,
                    )
                }
            }
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
                it.copy(errorMessage = result.exceptionOrNull()?.message)
            }
        }
    }

    fun onModelSelected(modelId: String) {
        viewModelScope.launch {
            val result = settingsRepository.setSelectedModel(modelId)
            mutableUiState.update {
                it.copy(errorMessage = result.exceptionOrNull()?.message)
            }
        }
    }

    fun onDefaultRingtoneSelected(ringtoneUri: String) {
        viewModelScope.launch {
            val result = settingsRepository.setDefaultRingtoneUri(ringtoneUri)
            mutableUiState.update {
                it.copy(errorMessage = result.exceptionOrNull()?.message)
            }
        }
    }
}
