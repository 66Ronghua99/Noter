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

data class VoiceHomeUiState(
    val status: VoiceHomeStatus = VoiceHomeStatus.Idle,
    val noticeMessage: UiText? = null,
    val errorMessage: UiText? = null,
    val showRetryAction: Boolean = false,
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

    fun onRecordPressed() {
        if (!microphonePermissionChecker.isGranted()) {
            mutableUiState.update {
                it.copy(
                    status = VoiceHomeStatus.PermissionNeeded,
                    noticeMessage = null,
                    errorMessage = UiText.Resource(R.string.voice_home_permission_needed),
                    showRetryAction = false,
                    showTextFallbackAction = true,
                    lastResult = null,
                )
            }
            return
        }

        viewModelScope.launch {
            val result = captureController.start()
            mutableUiState.update {
                result.toUiState(current = it, recordingStatus = VoiceHomeStatus.Recording)
            }
        }
    }

    fun onRecordReleased() {
        if (uiState.value.status != VoiceHomeStatus.Recording) {
            return
        }

        mutableUiState.update {
            it.copy(
                status = VoiceHomeStatus.Processing,
                noticeMessage = UiText.Resource(R.string.voice_home_processing_notice),
                errorMessage = null,
                showRetryAction = false,
                showTextFallbackAction = false,
            )
        }
        viewModelScope.launch {
            val result = captureController.release()
            mutableUiState.update {
                result.toUiState(current = it, recordingStatus = VoiceHomeStatus.Idle)
            }
        }
    }

    fun onRecordCancelled() {
        if (uiState.value.status != VoiceHomeStatus.Recording) {
            return
        }

        viewModelScope.launch {
            val result = captureController.cancel()
            mutableUiState.update {
                result.toUiState(current = it, recordingStatus = VoiceHomeStatus.Idle)
            }
        }
    }

    fun onRetry() {
        mutableUiState.update {
            it.copy(
                status = VoiceHomeStatus.Idle,
                noticeMessage = null,
                errorMessage = null,
                showRetryAction = false,
                showTextFallbackAction = false,
                lastResult = null,
            )
        }
    }

    private fun VoiceCaptureResult.toUiState(
        current: VoiceHomeUiState,
        recordingStatus: VoiceHomeStatus,
    ): VoiceHomeUiState = when (this) {
        VoiceCaptureResult.RecordingStarted -> current.copy(
            status = recordingStatus,
            noticeMessage = null,
            errorMessage = null,
            showRetryAction = false,
            showTextFallbackAction = false,
            lastResult = this,
        )

        VoiceCaptureResult.Cancelled -> current.copy(
            status = VoiceHomeStatus.Idle,
            noticeMessage = null,
            errorMessage = null,
            showRetryAction = false,
            showTextFallbackAction = false,
            lastResult = this,
        )

        is VoiceCaptureResult.Enqueued -> current.copy(
            status = VoiceHomeStatus.Idle,
            noticeMessage = UiText.Resource(R.string.voice_home_processing_notice),
            errorMessage = null,
            showRetryAction = false,
            showTextFallbackAction = false,
            lastResult = this,
        )

        is VoiceCaptureResult.Failed -> current.copy(
            status = VoiceHomeStatus.Idle,
            noticeMessage = null,
            errorMessage = failure.toUiText(),
            showRetryAction = failure !is VoiceCaptureFailure.MissingApiKey &&
                failure !is VoiceCaptureFailure.UnsupportedAsrModel,
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
}
