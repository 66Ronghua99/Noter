package com.cory.noter.ui.voice

import com.cory.noter.ai.AsrModel
import com.cory.noter.data.settings.FakeSettingsRepository
import com.cory.noter.domain.settings.AppSettings
import com.cory.noter.ui.MainDispatcherRule
import com.cory.noter.voice.ActiveSystemSpeechRecognition
import com.cory.noter.voice.ActiveTemporaryAudioRecording
import com.cory.noter.voice.MicrophonePermissionChecker
import com.cory.noter.voice.RecordedVoiceAudio
import com.cory.noter.voice.RemoteAsrTranscriber
import com.cory.noter.voice.SystemSpeechRecognizer
import com.cory.noter.voice.SystemSpeechResult
import com.cory.noter.voice.SystemSpeechStartResult
import com.cory.noter.voice.TemporaryAudioCleanup
import com.cory.noter.voice.TemporaryAudioHandle
import com.cory.noter.voice.TemporaryAudioRecorder
import com.cory.noter.voice.VoiceAiCreateEnqueuer
import com.cory.noter.voice.VoiceAsrRequest
import com.cory.noter.voice.VoiceAsrResult
import com.cory.noter.voice.VoiceCaptureController
import com.cory.noter.voice.VoiceCaptureCoordinator
import com.cory.noter.voice.VoiceCaptureFailure
import com.cory.noter.voice.VoiceCaptureResult
import com.cory.noter.voice.VoiceRecordingStartResult
import com.cory.noter.voice.VoiceRecordingStopResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceHomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `press with denied microphone permission exposes permission needed and does not start capture`() = runTest {
        val controller = RecordingVoiceCaptureController()
        val viewModel = VoiceHomeViewModel(
            microphonePermissionChecker = MicrophonePermissionChecker { false },
            captureController = controller,
        )

        viewModel.onRecordPressed()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.status).isEqualTo(VoiceHomeStatus.PermissionNeeded)
        assertThat(controller.startCalls).isEqualTo(0)
    }

    @Test
    fun `press starts capture and release transcribes when not cancelled`() = runTest {
        val controller = RecordingVoiceCaptureController(
            releaseResult = VoiceCaptureResult.Enqueued("wake me at eight"),
        )
        val viewModel = VoiceHomeViewModel(
            microphonePermissionChecker = MicrophonePermissionChecker { true },
            captureController = controller,
        )

        viewModel.onRecordPressed()
        advanceUntilIdle()
        viewModel.onRecordReleased()
        advanceUntilIdle()

        assertThat(controller.startCalls).isEqualTo(1)
        assertThat(controller.releaseCalls).isEqualTo(1)
        assertThat(controller.cancelCalls).isEqualTo(0)
        assertThat(viewModel.uiState.value.status).isEqualTo(VoiceHomeStatus.Idle)
        assertThat(viewModel.uiState.value.lastResult)
            .isEqualTo(VoiceCaptureResult.Enqueued("wake me at eight"))
    }

    @Test
    fun `cancel stops capture and later release does not enqueue`() = runTest {
        val recorder = FakeTemporaryAudioRecorder()
        val systemSpeech = FakeSystemSpeechRecognizer(
            stopResult = SystemSpeechResult.Transcript("wake me at eight"),
        )
        val enqueuer = RecordingVoiceAiCreateEnqueuer()
        val coordinator = newCoordinator(
            recorder = recorder,
            systemSpeechRecognizer = systemSpeech,
            enqueuer = enqueuer,
        )

        assertThat(coordinator.start()).isEqualTo(VoiceCaptureResult.RecordingStarted)
        assertThat(coordinator.cancel()).isEqualTo(VoiceCaptureResult.Cancelled)
        val releaseAfterCancel = coordinator.release()

        assertThat(recorder.active.cancelCalls).isEqualTo(1)
        assertThat(enqueuer.transcripts).isEmpty()
        assertThat(releaseAfterCancel)
            .isEqualTo(VoiceCaptureResult.Failed(VoiceCaptureFailure.NoActiveRecording))
    }

    @Test
    fun `system stt success enqueues transcript bypasses openrouter asr and cleans up audio`() = runTest {
        val recorder = FakeTemporaryAudioRecorder(recordedBytes = "recorded-audio".encodeToByteArray())
        val systemSpeech = FakeSystemSpeechRecognizer(
            stopResult = SystemSpeechResult.Transcript("wake me at eight"),
        )
        val remoteAsr = RecordingRemoteAsrTranscriber()
        val cleanup = RecordingTemporaryAudioCleanup()
        val enqueuer = RecordingVoiceAiCreateEnqueuer()
        val coordinator = newCoordinator(
            recorder = recorder,
            systemSpeechRecognizer = systemSpeech,
            remoteAsr = remoteAsr,
            cleanup = cleanup,
            enqueuer = enqueuer,
        )

        assertThat(coordinator.start()).isEqualTo(VoiceCaptureResult.RecordingStarted)
        val result = coordinator.release()

        assertThat(result).isEqualTo(VoiceCaptureResult.Enqueued("wake me at eight"))
        assertThat(remoteAsr.requests).isEmpty()
        assertThat(enqueuer.transcripts).containsExactly("wake me at eight")
        assertThat(cleanup.cleanedHandles).containsExactly(TemporaryAudioHandle("voice-temp"))
    }

    @Test
    fun `blank system stt transcript fails before enqueue and before openrouter asr`() = runTest {
        val remoteAsr = RecordingRemoteAsrTranscriber()
        val enqueuer = RecordingVoiceAiCreateEnqueuer()
        val cleanup = RecordingTemporaryAudioCleanup()
        val coordinator = newCoordinator(
            systemSpeechRecognizer = FakeSystemSpeechRecognizer(
                stopResult = SystemSpeechResult.Transcript("   "),
            ),
            remoteAsr = remoteAsr,
            cleanup = cleanup,
            enqueuer = enqueuer,
        )

        assertThat(coordinator.start()).isEqualTo(VoiceCaptureResult.RecordingStarted)
        val result = coordinator.release()

        assertThat(result).isEqualTo(VoiceCaptureResult.Failed(VoiceCaptureFailure.BlankTranscript))
        assertThat(remoteAsr.requests).isEmpty()
        assertThat(enqueuer.transcripts).isEmpty()
        assertThat(cleanup.cleanedHandles).containsExactly(TemporaryAudioHandle("voice-temp"))
    }

    @Test
    fun `system stt failure falls back to openrouter asr with same audio selected model and api key`() = runTest {
        val recordedBytes = "recorded-audio".encodeToByteArray()
        val remoteAsr = RecordingRemoteAsrTranscriber(
            nextResult = VoiceAsrResult.Transcript("set an alarm for nine"),
        )
        val enqueuer = RecordingVoiceAiCreateEnqueuer()
        val coordinator = newCoordinator(
            recorder = FakeTemporaryAudioRecorder(recordedBytes = recordedBytes),
            systemSpeechRecognizer = FakeSystemSpeechRecognizer(
                stopResult = SystemSpeechResult.Failed("system stt unavailable"),
            ),
            remoteAsr = remoteAsr,
            enqueuer = enqueuer,
        )

        assertThat(coordinator.start()).isEqualTo(VoiceCaptureResult.RecordingStarted)
        val result = coordinator.release()

        assertThat(result).isEqualTo(VoiceCaptureResult.Enqueued("set an alarm for nine"))
        assertThat(remoteAsr.requests).hasSize(1)
        val request = remoteAsr.requests.single()
        assertThat(request.apiKey).isEqualTo("sk-or-v1-test")
        assertThat(request.modelId).isEqualTo("mistralai/voxtral-mini-transcribe")
        assertThat(request.audio.bytes).isEqualTo(recordedBytes)
        assertThat(enqueuer.transcripts).containsExactly("set an alarm for nine")
    }

    @Test
    fun `missing api key fails explicitly before openrouter asr fallback`() = runTest {
        val remoteAsr = RecordingRemoteAsrTranscriber()
        val cleanup = RecordingTemporaryAudioCleanup()
        val coordinator = newCoordinator(
            settingsRepository = FakeSettingsRepository(
                initialSettings = voiceSettings(openRouterApiKey = "   "),
            ),
            systemSpeechRecognizer = FakeSystemSpeechRecognizer(
                stopResult = SystemSpeechResult.Failed("system stt unavailable"),
            ),
            remoteAsr = remoteAsr,
            cleanup = cleanup,
        )

        assertThat(coordinator.start()).isEqualTo(VoiceCaptureResult.RecordingStarted)
        val result = coordinator.release()

        assertThat(result).isEqualTo(VoiceCaptureResult.Failed(VoiceCaptureFailure.MissingApiKey))
        assertThat(remoteAsr.requests).isEmpty()
        assertThat(cleanup.cleanedHandles).containsExactly(TemporaryAudioHandle("voice-temp"))
    }

    @Test
    fun `unsupported asr model fails explicitly before openrouter asr fallback`() = runTest {
        val remoteAsr = RecordingRemoteAsrTranscriber()
        val cleanup = RecordingTemporaryAudioCleanup()
        val coordinator = newCoordinator(
            settingsRepository = FakeSettingsRepository(
                initialSettings = voiceSettings(selectedAsrModelId = "unknown/asr"),
            ),
            systemSpeechRecognizer = FakeSystemSpeechRecognizer(
                stopResult = SystemSpeechResult.Failed("system stt unavailable"),
            ),
            remoteAsr = remoteAsr,
            cleanup = cleanup,
        )

        assertThat(coordinator.start()).isEqualTo(VoiceCaptureResult.RecordingStarted)
        val result = coordinator.release()

        assertThat(result)
            .isEqualTo(VoiceCaptureResult.Failed(VoiceCaptureFailure.UnsupportedAsrModel("unknown/asr")))
        assertThat(remoteAsr.requests).isEmpty()
        assertThat(cleanup.cleanedHandles).containsExactly(TemporaryAudioHandle("voice-temp"))
    }

    @Test
    fun `openrouter asr failure does not enqueue and still cleans up audio`() = runTest {
        val cleanup = RecordingTemporaryAudioCleanup()
        val enqueuer = RecordingVoiceAiCreateEnqueuer()
        val coordinator = newCoordinator(
            systemSpeechRecognizer = FakeSystemSpeechRecognizer(
                stopResult = SystemSpeechResult.Failed("system stt unavailable"),
            ),
            remoteAsr = RecordingRemoteAsrTranscriber(
                nextResult = VoiceAsrResult.Failed("remote asr failed"),
            ),
            cleanup = cleanup,
            enqueuer = enqueuer,
        )

        assertThat(coordinator.start()).isEqualTo(VoiceCaptureResult.RecordingStarted)
        val result = coordinator.release()

        assertThat(result)
            .isEqualTo(VoiceCaptureResult.Failed(VoiceCaptureFailure.AsrFailed("remote asr failed")))
        assertThat(enqueuer.transcripts).isEmpty()
        assertThat(cleanup.cleanedHandles).containsExactly(TemporaryAudioHandle("voice-temp"))
    }

    private fun newCoordinator(
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(
            initialSettings = voiceSettings(),
        ),
        recorder: FakeTemporaryAudioRecorder = FakeTemporaryAudioRecorder(),
        systemSpeechRecognizer: FakeSystemSpeechRecognizer = FakeSystemSpeechRecognizer(),
        remoteAsr: RecordingRemoteAsrTranscriber = RecordingRemoteAsrTranscriber(),
        cleanup: RecordingTemporaryAudioCleanup = RecordingTemporaryAudioCleanup(),
        enqueuer: RecordingVoiceAiCreateEnqueuer = RecordingVoiceAiCreateEnqueuer(),
    ): VoiceCaptureCoordinator = VoiceCaptureCoordinator(
        settingsRepository = settingsRepository,
        temporaryAudioRecorder = recorder,
        systemSpeechRecognizer = systemSpeechRecognizer,
        remoteAsrTranscriber = remoteAsr,
        temporaryAudioCleanup = cleanup,
        aiCreateEnqueuer = enqueuer,
    )

    private class RecordingVoiceCaptureController(
        private val startResult: VoiceCaptureResult = VoiceCaptureResult.RecordingStarted,
        private val releaseResult: VoiceCaptureResult = VoiceCaptureResult.Cancelled,
        private val cancelResult: VoiceCaptureResult = VoiceCaptureResult.Cancelled,
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
            return cancelResult
        }
    }

    private class FakeTemporaryAudioRecorder(
        recordedBytes: ByteArray = "recorded-audio".encodeToByteArray(),
    ) : TemporaryAudioRecorder {
        val active = FakeActiveTemporaryAudioRecording(recordedBytes)
        var startCalls = 0

        override suspend fun start(): VoiceRecordingStartResult {
            startCalls += 1
            return VoiceRecordingStartResult.Started(active)
        }
    }

    private class FakeActiveTemporaryAudioRecording(
        private val recordedBytes: ByteArray,
    ) : ActiveTemporaryAudioRecording {
        override val handle: TemporaryAudioHandle = TemporaryAudioHandle("voice-temp")
        var stopCalls = 0
        var cancelCalls = 0

        override suspend fun stop(): VoiceRecordingStopResult {
            stopCalls += 1
            return VoiceRecordingStopResult.Recorded(
                RecordedVoiceAudio(
                    handle = handle,
                    bytes = recordedBytes,
                ),
            )
        }

        override suspend fun cancel(): VoiceRecordingStopResult {
            cancelCalls += 1
            return VoiceRecordingStopResult.Cancelled
        }
    }

    private class FakeSystemSpeechRecognizer(
        private val startResult: SystemSpeechStartResult? = null,
        private val stopResult: SystemSpeechResult = SystemSpeechResult.Failed("system stt unavailable"),
    ) : SystemSpeechRecognizer {
        override suspend fun start(): SystemSpeechStartResult =
            startResult ?: SystemSpeechStartResult.Started(FakeActiveSystemSpeechRecognition(stopResult))
    }

    private class FakeActiveSystemSpeechRecognition(
        private val stopResult: SystemSpeechResult,
    ) : ActiveSystemSpeechRecognition {
        var cancelCalls = 0

        override suspend fun stopAndTranscribe(): SystemSpeechResult = stopResult

        override suspend fun cancel() {
            cancelCalls += 1
        }
    }

    private class RecordingRemoteAsrTranscriber(
        private val nextResult: VoiceAsrResult = VoiceAsrResult.Failed("not configured"),
    ) : RemoteAsrTranscriber {
        val requests = mutableListOf<VoiceAsrRequest>()

        override suspend fun transcribe(request: VoiceAsrRequest): VoiceAsrResult {
            requests += request
            return nextResult
        }
    }

    private class RecordingTemporaryAudioCleanup : TemporaryAudioCleanup {
        val cleanedHandles = mutableListOf<TemporaryAudioHandle>()

        override suspend fun cleanup(handle: TemporaryAudioHandle) {
            cleanedHandles += handle
        }
    }

    private class RecordingVoiceAiCreateEnqueuer : VoiceAiCreateEnqueuer {
        val transcripts = mutableListOf<String>()

        override fun enqueue(transcript: String) {
            transcripts += transcript
        }
    }
}

private fun voiceSettings(
    openRouterApiKey: String = "sk-or-v1-test",
    selectedAsrModelId: String = "mistralai/voxtral-mini-transcribe",
): AppSettings = AppSettings(
    openRouterApiKey = openRouterApiKey,
    selectedModelId = "deepseek/deepseek-v4-flash",
    selectedAsrModelId = selectedAsrModelId,
    defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
)
