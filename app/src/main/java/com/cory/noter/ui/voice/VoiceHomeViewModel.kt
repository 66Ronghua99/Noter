package com.cory.noter.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cory.noter.voice.MicrophonePermissionChecker
import com.cory.noter.voice.VoiceCaptureController
import com.cory.noter.voice.VoiceCaptureResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceHomeUiState(
    val status: VoiceHomeStatus = VoiceHomeStatus.Idle,
    val lastResult: VoiceCaptureResult? = null,
)

enum class VoiceHomeStatus {
    Idle,
    Recording,
    Processing,
    PermissionNeeded,
}

class VoiceHomeViewModel(
    private val microphonePermissionChecker: MicrophonePermissionChecker,
    private val captureController: VoiceCaptureController,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(VoiceHomeUiState())
    val uiState: StateFlow<VoiceHomeUiState> = mutableUiState.asStateFlow()

    fun onRecordPressed() {
        if (!microphonePermissionChecker.isGranted()) {
            mutableUiState.update {
                it.copy(
                    status = VoiceHomeStatus.PermissionNeeded,
                    lastResult = null,
                )
            }
            return
        }

        viewModelScope.launch {
            val result = captureController.start()
            mutableUiState.update {
                it.copy(
                    status = if (result == VoiceCaptureResult.RecordingStarted) {
                        VoiceHomeStatus.Recording
                    } else {
                        VoiceHomeStatus.Idle
                    },
                    lastResult = result,
                )
            }
        }
    }

    fun onRecordReleased() {
        mutableUiState.update {
            it.copy(
                status = VoiceHomeStatus.Processing,
                lastResult = null,
            )
        }
        viewModelScope.launch {
            val result = captureController.release()
            mutableUiState.update {
                it.copy(
                    status = VoiceHomeStatus.Idle,
                    lastResult = result,
                )
            }
        }
    }

    fun onRecordCancelled() {
        viewModelScope.launch {
            val result = captureController.cancel()
            mutableUiState.update {
                it.copy(
                    status = VoiceHomeStatus.Idle,
                    lastResult = result,
                )
            }
        }
    }
}
