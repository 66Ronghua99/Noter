package com.cory.noter.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cory.noter.R
import com.cory.noter.ui.text.UiText
import com.cory.noter.voice.MicrophonePermissionChecker
import com.cory.noter.voice.VoiceCaptureController
import com.cory.noter.voice.VoiceCaptureFailure
import com.cory.noter.voice.VoiceCaptureResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class VoiceHomeUiState(
    val status: VoiceHomeStatus = VoiceHomeStatus.Idle,
    val noticeMessage: UiText? = null,
    val errorMessage: UiText? = null,
    val showRetryAction: Boolean = false,
    val showPermissionRecoveryAction: Boolean = false,
    val showTextFallbackAction: Boolean = false,
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
    private var startInFlight = false
    private var pendingTerminalAction: PendingTerminalAction? = null

    fun onRecordPressed() {
        if (!microphonePermissionChecker.isGranted()) {
            mutableUiState.update {
                it.copy(
                    status = VoiceHomeStatus.PermissionNeeded,
                    noticeMessage = null,
                    errorMessage = UiText.Resource(R.string.voice_home_permission_needed),
                    showRetryAction = false,
                    showPermissionRecoveryAction = true,
                    showTextFallbackAction = true,
                    lastResult = null,
                )
            }
            return
        }

        if (startInFlight || uiState.value.status == VoiceHomeStatus.Recording ||
            uiState.value.status == VoiceHomeStatus.Processing
        ) {
            return
        }

        startInFlight = true
        pendingTerminalAction = null
        mutableUiState.update {
            it.copy(
                status = VoiceHomeStatus.Recording,
                noticeMessage = null,
                errorMessage = null,
                showRetryAction = false,
                showPermissionRecoveryAction = false,
                showTextFallbackAction = false,
                lastResult = null,
            )
        }

        viewModelScope.launch {
            val result = captureController.start()
            val terminalAction = pendingTerminalAction
            startInFlight = false
            pendingTerminalAction = null

            if (result == VoiceCaptureResult.RecordingStarted && terminalAction != null) {
                when (terminalAction) {
                    PendingTerminalAction.Release -> releaseCapture()
                    PendingTerminalAction.Cancel -> cancelCapture()
                }
            } else {
                mutableUiState.update {
                    result.toUiState(current = it, recordingStatus = VoiceHomeStatus.Recording)
                }
            }
        }
    }

    fun onRecordReleased() {
        if (uiState.value.status != VoiceHomeStatus.Recording) {
            return
        }

        if (startInFlight) {
            pendingTerminalAction = PendingTerminalAction.Release
            mutableUiState.update { it.toProcessingState() }
            return
        }

        viewModelScope.launch {
            releaseCapture()
        }
    }

    fun onRecordCancelled() {
        if (uiState.value.status != VoiceHomeStatus.Recording) {
            return
        }

        if (startInFlight) {
            pendingTerminalAction = PendingTerminalAction.Cancel
            mutableUiState.update { it.toIdleAfterCancelState() }
            return
        }

        viewModelScope.launch {
            cancelCapture()
        }
    }

    fun onRetry() {
        mutableUiState.update {
            it.copy(
                status = VoiceHomeStatus.Idle,
                noticeMessage = null,
                errorMessage = null,
                showRetryAction = false,
                showPermissionRecoveryAction = false,
                showTextFallbackAction = false,
                lastResult = null,
            )
        }
    }

    override fun onCleared() {
        if (startInFlight || uiState.value.status == VoiceHomeStatus.Recording ||
            uiState.value.status == VoiceHomeStatus.Processing
        ) {
            runBlocking {
                captureController.cancel()
            }
        }
        super.onCleared()
    }

    private suspend fun releaseCapture() {
        mutableUiState.update { it.toProcessingState() }
        val result = captureController.release()
        mutableUiState.update {
            result.toUiState(current = it, recordingStatus = VoiceHomeStatus.Idle)
        }
    }

    private suspend fun cancelCapture() {
        val result = captureController.cancel()
        mutableUiState.update {
            result.toUiState(current = it, recordingStatus = VoiceHomeStatus.Idle)
        }
    }

    private fun VoiceHomeUiState.toProcessingState(): VoiceHomeUiState = copy(
        status = VoiceHomeStatus.Processing,
        noticeMessage = UiText.Resource(R.string.voice_home_processing_notice),
        errorMessage = null,
        showRetryAction = false,
        showPermissionRecoveryAction = false,
        showTextFallbackAction = false,
    )

    private fun VoiceHomeUiState.toIdleAfterCancelState(): VoiceHomeUiState = copy(
        status = VoiceHomeStatus.Idle,
        noticeMessage = null,
        errorMessage = null,
        showRetryAction = false,
        showPermissionRecoveryAction = false,
        showTextFallbackAction = false,
    )

    private fun VoiceCaptureResult.toUiState(
        current: VoiceHomeUiState,
        recordingStatus: VoiceHomeStatus,
    ): VoiceHomeUiState = when (this) {
        VoiceCaptureResult.RecordingStarted -> current.copy(
            status = recordingStatus,
            noticeMessage = null,
            errorMessage = null,
            showRetryAction = false,
            showPermissionRecoveryAction = false,
            showTextFallbackAction = false,
            lastResult = this,
        )

        VoiceCaptureResult.Cancelled -> current.copy(
            status = VoiceHomeStatus.Idle,
            noticeMessage = null,
            errorMessage = null,
            showRetryAction = false,
            showPermissionRecoveryAction = false,
            showTextFallbackAction = false,
            lastResult = this,
        )

        is VoiceCaptureResult.Enqueued -> current.copy(
            status = VoiceHomeStatus.Idle,
            noticeMessage = UiText.Resource(R.string.voice_home_processing_notice),
            errorMessage = null,
            showRetryAction = false,
            showPermissionRecoveryAction = false,
            showTextFallbackAction = false,
            lastResult = this,
        )

        is VoiceCaptureResult.Failed -> current.copy(
            status = VoiceHomeStatus.Idle,
            noticeMessage = null,
            errorMessage = failure.toUiText(),
            showRetryAction = failure !is VoiceCaptureFailure.MissingApiKey &&
                failure !is VoiceCaptureFailure.UnsupportedAsrModel,
            showPermissionRecoveryAction = false,
            showTextFallbackAction = true,
            lastResult = this,
        )
    }

    private fun VoiceCaptureFailure.toUiText(): UiText = when (this) {
        VoiceCaptureFailure.AlreadyRecording -> UiText.Resource(R.string.voice_home_already_recording)
        VoiceCaptureFailure.NoActiveRecording -> UiText.Resource(R.string.voice_home_no_active_recording)
        VoiceCaptureFailure.BlankTranscript -> UiText.Resource(R.string.voice_home_blank_transcript)
        VoiceCaptureFailure.MissingApiKey -> UiText.Resource(R.string.voice_home_missing_api_key)
        is VoiceCaptureFailure.UnsupportedAsrModel -> {
            UiText.Resource(R.string.voice_home_unsupported_asr_model, listOf(modelId))
        }
        is VoiceCaptureFailure.RecordingFailed -> UiText.Resource(R.string.voice_home_recording_failed, listOf(reason))
        is VoiceCaptureFailure.AsrFailed -> UiText.Resource(R.string.voice_home_asr_failed, listOf(reason))
    }

    private enum class PendingTerminalAction {
        Release,
        Cancel,
    }
}
