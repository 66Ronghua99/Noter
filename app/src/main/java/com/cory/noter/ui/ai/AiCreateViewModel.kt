package com.cory.noter.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cory.noter.ai.AiAlarmCreator
import com.cory.noter.ai.AiCreateResult
import com.cory.noter.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiCreateUiState(
    val prompt: String = "",
    val selectedModelId: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val createdAlarmId: Long? = null,
)

class AiCreateViewModel(
    private val creator: AiAlarmCreator,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AiCreateUiState())
    val uiState: StateFlow<AiCreateUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                mutableUiState.update { current ->
                    current.copy(selectedModelId = settings.selectedModelId)
                }
            }
        }
    }

    fun onPromptChanged(prompt: String) {
        mutableUiState.update {
            it.copy(
                prompt = prompt,
                errorMessage = null,
            )
        }
    }

    fun submit() {
        val prompt = uiState.value.prompt.trim()
        if (prompt.isEmpty()) {
            mutableUiState.update {
                it.copy(errorMessage = "Describe the alarm you want to create.")
            }
            return
        }

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            val result = creator.createFromText(prompt)
            mutableUiState.update { current ->
                current.copy(
                    isLoading = false,
                    errorMessage = result.toErrorMessage(),
                    createdAlarmId = (result as? AiCreateResult.Created)?.alarm?.id,
                )
            }
        }
    }

    private fun AiCreateResult.toErrorMessage(): String? = when (this) {
        AiCreateResult.MissingApiKey ->
            "Add an OpenRouter API key in Settings before using AI create."

        AiCreateResult.MissingModel ->
            "Choose a supported free model in Settings before using AI create."

        is AiCreateResult.NetworkFailure -> "Network error: $reason"
        is AiCreateResult.RateLimited -> "Model rate limit: $reason"
        is AiCreateResult.RemoteFailure -> "OpenRouter error $code: $reason"
        is AiCreateResult.InvalidResponse -> reason
        is AiCreateResult.ClarificationRequired -> reason
        is AiCreateResult.CreateFailed -> reason
        is AiCreateResult.MissingSchedulingPermission ->
            "Alarm saved but scheduling needs permission: $permission"

        is AiCreateResult.ScheduleFailed -> reason
        is AiCreateResult.Created -> null
    }
}
