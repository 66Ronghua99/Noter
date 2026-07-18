package com.cory.noter.ui.voice

import com.cory.noter.R
import com.cory.noter.ui.MainDispatcherRule
import com.cory.noter.voice.MicrophonePermissionChecker
import com.cory.noter.voice.VoiceCaptureController
import com.cory.noter.voice.VoiceCaptureFailure
import com.cory.noter.voice.VoiceCaptureResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class VoiceHomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `missing microphone permission exposes recovery actions`() = runTest {
        val controller = RecordingVoiceCaptureController()
        val viewModel = VoiceHomeViewModel(
            microphonePermissionChecker = MicrophonePermissionChecker { false },
            captureController = controller,
        )

        viewModel.onRecordPressed()

        assertThat(viewModel.uiState.value.status).isEqualTo(VoiceHomeStatus.PermissionNeeded)
        assertThat(viewModel.uiState.value.showPermissionRecoveryAction).isTrue()
        assertThat(viewModel.uiState.value.showTextFallbackAction).isTrue()
        assertThat(controller.startCalls).isEqualTo(0)
    }

    @Test
    fun `remote recording flow exposes processing and enqueued transcript`() = runTest {
        val controller = RecordingVoiceCaptureController(
            startResult = VoiceCaptureResult.RecordingStarted,
            releaseResult = VoiceCaptureResult.Enqueued("set an alarm for nine"),
        )
        val viewModel = readyViewModel(controller)

        viewModel.onRecordPressed()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.status).isEqualTo(VoiceHomeStatus.Recording)

        viewModel.onRecordReleased()
        advanceUntilIdle()

        assertThat(controller.releaseCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value.lastResult)
            .isEqualTo(VoiceCaptureResult.Enqueued("set an alarm for nine"))
        assertThat(viewModel.uiState.value.noticeMessage).isNotNull()
    }

    @Test
    fun `remote failure exposes retry and text fallback without provider details`() = runTest {
        val controller = RecordingVoiceCaptureController(
            startResult = VoiceCaptureResult.RecordingStarted,
            releaseResult = VoiceCaptureResult.Failed(
                VoiceCaptureFailure.AsrFailed("service_unavailable"),
            ),
        )
        val viewModel = readyViewModel(controller)

        viewModel.onRecordPressed()
        advanceUntilIdle()
        viewModel.onRecordReleased()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showRetryAction).isTrue()
        assertThat(viewModel.uiState.value.showTextFallbackAction).isTrue()
        assertThat(viewModel.uiState.value.errorMessage).isNotNull()
        assertThat(viewModel.uiState.value.errorMessage.toString()).doesNotContain("OpenRouter")
    }

    @Test
    fun `update required failure suppresses retry but keeps text fallback`() = runTest {
        val controller = RecordingVoiceCaptureController(
            startResult = VoiceCaptureResult.RecordingStarted,
            releaseResult = VoiceCaptureResult.Failed(VoiceCaptureFailure.UpdateRequired),
        )
        val viewModel = readyViewModel(controller)

        viewModel.onRecordPressed()
        advanceUntilIdle()
        viewModel.onRecordReleased()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showRetryAction).isFalse()
        assertThat(viewModel.uiState.value.showTextFallbackAction).isTrue()
    }

    @Test
    fun `retry returns voice surface to idle`() = runTest {
        val viewModel = readyViewModel(RecordingVoiceCaptureController())

        viewModel.onRetry()

        assertThat(viewModel.uiState.value.status).isEqualTo(VoiceHomeStatus.Idle)
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    private fun readyViewModel(controller: RecordingVoiceCaptureController): VoiceHomeViewModel =
        VoiceHomeViewModel(
            microphonePermissionChecker = MicrophonePermissionChecker { true },
            captureController = controller,
        )

    private class RecordingVoiceCaptureController(
        private val startResult: VoiceCaptureResult = VoiceCaptureResult.RecordingStarted,
        private val releaseResult: VoiceCaptureResult = VoiceCaptureResult.Cancelled,
    ) : VoiceCaptureController {
        var startCalls = 0
        var releaseCalls = 0
        var cancelCalls = 0

        override suspend fun start(): VoiceCaptureResult {
            startCalls += 1
            return startResult
        }

        override suspend fun release(): VoiceCaptureResult {
            releaseCalls += 1
            return releaseResult
        }

        override suspend fun cancel(): VoiceCaptureResult {
            cancelCalls += 1
            return VoiceCaptureResult.Cancelled
        }
    }
}
