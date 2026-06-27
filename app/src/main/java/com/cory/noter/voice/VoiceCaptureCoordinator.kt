package com.cory.noter.voice

import com.cory.noter.ai.AsrModel
import com.cory.noter.data.settings.SettingsRepository
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

fun interface MicrophonePermissionChecker {
    fun isGranted(): Boolean
}

interface VoiceCaptureController {
    suspend fun start(): VoiceCaptureResult

    suspend fun release(): VoiceCaptureResult

    suspend fun cancel(): VoiceCaptureResult
}

interface TemporaryAudioRecorder {
    suspend fun start(): VoiceRecordingStartResult
}

interface ActiveTemporaryAudioRecording {
    val handle: TemporaryAudioHandle

    suspend fun stop(): VoiceRecordingStopResult

    suspend fun cancel(): VoiceRecordingStopResult
}

interface SystemSpeechRecognizer {
    suspend fun start(): SystemSpeechStartResult
}

interface ActiveSystemSpeechRecognition {
    suspend fun stopAndTranscribe(): SystemSpeechResult

    suspend fun cancel()
}

interface RemoteAsrTranscriber {
    suspend fun transcribe(request: VoiceAsrRequest): VoiceAsrResult
}

fun interface VoiceAsrLanguageProvider {
    fun languageCode(): String
}

interface TemporaryAudioCleanup {
    suspend fun cleanup(handle: TemporaryAudioHandle)
}

interface VoiceAiCreateEnqueuer {
    fun enqueue(transcript: String)
}

interface VoiceCaptureDebugLogger {
    fun debug(message: String)

    fun warn(message: String, error: Throwable? = null)

    object None : VoiceCaptureDebugLogger {
        override fun debug(message: String) = Unit

        override fun warn(message: String, error: Throwable?) = Unit
    }
}

data class TemporaryAudioHandle(val id: String)

class RecordedVoiceAudio(
    val handle: TemporaryAudioHandle,
    val bytes: ByteArray,
)

class VoiceAsrRequest(
    val apiKey: String,
    val modelId: String,
    val languageCode: String,
    val audio: RecordedVoiceAudio,
)

sealed interface VoiceRecordingStartResult {
    data class Started(val recording: ActiveTemporaryAudioRecording) : VoiceRecordingStartResult

    data class Failed(val reason: String) : VoiceRecordingStartResult
}

sealed interface VoiceRecordingStopResult {
    data class Recorded(val audio: RecordedVoiceAudio) : VoiceRecordingStopResult

    data class Failed(val reason: String) : VoiceRecordingStopResult

    data object Cancelled : VoiceRecordingStopResult
}

sealed interface SystemSpeechStartResult {
    data class Started(val recognition: ActiveSystemSpeechRecognition) : SystemSpeechStartResult

    data class Failed(val reason: String) : SystemSpeechStartResult
}

sealed interface SystemSpeechResult {
    data class Transcript(val text: String) : SystemSpeechResult

    data class Failed(val reason: String) : SystemSpeechResult
}

sealed interface VoiceAsrResult {
    data class Transcript(val text: String) : VoiceAsrResult

    data class Failed(val reason: String) : VoiceAsrResult
}

sealed interface VoiceCaptureResult {
    data object RecordingStarted : VoiceCaptureResult

    data object Cancelled : VoiceCaptureResult

    data class Enqueued(val transcript: String) : VoiceCaptureResult

    data class Failed(val failure: VoiceCaptureFailure) : VoiceCaptureResult
}

sealed interface VoiceCaptureFailure {
    data object AlreadyRecording : VoiceCaptureFailure

    data object NoActiveRecording : VoiceCaptureFailure

    data object BlankTranscript : VoiceCaptureFailure

    data object MissingApiKey : VoiceCaptureFailure

    data class UnsupportedAsrModel(val modelId: String) : VoiceCaptureFailure

    data class RecordingFailed(val reason: String) : VoiceCaptureFailure

    data class AsrFailed(val reason: String) : VoiceCaptureFailure
}

class VoiceCaptureCoordinator(
    private val settingsRepository: SettingsRepository,
    private val temporaryAudioRecorder: TemporaryAudioRecorder,
    private val systemSpeechRecognizer: SystemSpeechRecognizer,
    private val remoteAsrTranscriber: RemoteAsrTranscriber,
    private val temporaryAudioCleanup: TemporaryAudioCleanup,
    private val aiCreateEnqueuer: VoiceAiCreateEnqueuer,
    private val asrLanguageProvider: VoiceAsrLanguageProvider,
    private val debugLogger: VoiceCaptureDebugLogger = VoiceCaptureDebugLogger.None,
) : VoiceCaptureController {
    private val logger = Logger.getLogger(VoiceCaptureCoordinator::class.java.name)
    private var activeCapture: ActiveVoiceCapture? = null

    override suspend fun start(): VoiceCaptureResult {
        if (activeCapture != null) {
            return VoiceCaptureResult.Failed(VoiceCaptureFailure.AlreadyRecording)
        }

        val recording = when (val startResult = temporaryAudioRecorder.start()) {
            is VoiceRecordingStartResult.Started -> {
                debugLogger.debug("voice.recording.started handle=${startResult.recording.handle.id}")
                startResult.recording
            }
            is VoiceRecordingStartResult.Failed -> {
                debugLogger.debug("voice.recording.start.failed reason=${startResult.reason}")
                return VoiceCaptureResult.Failed(VoiceCaptureFailure.RecordingFailed(startResult.reason))
            }
        }

        val systemSpeech = when (val speechStart = systemSpeechRecognizer.start()) {
            is SystemSpeechStartResult.Started -> {
                debugLogger.debug("voice.systemStt.started")
                speechStart.recognition
            }
            is SystemSpeechStartResult.Failed -> {
                debugLogger.debug("voice.systemStt.start.failed reason=${speechStart.reason}")
                null
            }
        }

        activeCapture = ActiveVoiceCapture(
            recording = recording,
            systemSpeechRecognition = systemSpeech,
        )
        return VoiceCaptureResult.RecordingStarted
    }

    override suspend fun release(): VoiceCaptureResult {
        val capture = activeCapture ?: return VoiceCaptureResult.Failed(VoiceCaptureFailure.NoActiveRecording)
        activeCapture = null

        return when (val stopResult = capture.recording.stop()) {
            is VoiceRecordingStopResult.Recorded -> processRecordedAudio(capture, stopResult.audio)
            is VoiceRecordingStopResult.Failed -> {
                debugLogger.debug("voice.recording.stop.failed reason=${stopResult.reason}")
                capture.systemSpeechRecognition?.cancel()
                cleanupWithoutMasking(capture.recording.handle)
                VoiceCaptureResult.Failed(VoiceCaptureFailure.RecordingFailed(stopResult.reason))
            }
            VoiceRecordingStopResult.Cancelled -> {
                capture.systemSpeechRecognition?.cancel()
                cleanupWithoutMasking(capture.recording.handle)
                VoiceCaptureResult.Cancelled
            }
        }
    }

    override suspend fun cancel(): VoiceCaptureResult {
        val capture = activeCapture ?: return VoiceCaptureResult.Failed(VoiceCaptureFailure.NoActiveRecording)
        activeCapture = null

        capture.systemSpeechRecognition?.cancel()
        capture.recording.cancel()
        cleanupWithoutMasking(capture.recording.handle)
        return VoiceCaptureResult.Cancelled
    }

    private suspend fun processRecordedAudio(
        capture: ActiveVoiceCapture,
        audio: RecordedVoiceAudio,
    ): VoiceCaptureResult {
        try {
            val systemSpeech = capture.systemSpeechRecognition
            if (systemSpeech != null) {
                return when (val speechResult = systemSpeech.stopAndTranscribe()) {
                    is SystemSpeechResult.Transcript -> {
                        debugLogger.debug("voice.systemStt.transcript chars=${speechResult.text.trim().length}")
                        enqueueTranscriptOrFail(speechResult.text)
                    }
                    is SystemSpeechResult.Failed -> {
                        debugLogger.debug("voice.systemStt.result.failed reason=${speechResult.reason}")
                        transcribeWithRemoteAsr(audio)
                    }
                }
            }

            return transcribeWithRemoteAsr(audio)
        } finally {
            cleanupWithoutMasking(audio.handle)
        }
    }

    private suspend fun cleanupWithoutMasking(handle: TemporaryAudioHandle) {
        try {
            temporaryAudioCleanup.cleanup(handle)
        } catch (error: Exception) {
            if (error is CancellationException) {
                throw error
            }
            debugLogger.warn("voice.cleanup.failed handle=${handle.id}", error)
            logger.log(Level.WARNING, "Temporary voice audio cleanup failed for ${handle.id}.", error)
        }
    }

    private suspend fun transcribeWithRemoteAsr(audio: RecordedVoiceAudio): VoiceCaptureResult {
        val settings = settingsRepository.settings.first()
        val apiKey = settings.openRouterApiKey.trim()
        if (apiKey.isEmpty()) {
            debugLogger.debug("voice.remoteAsr.missingApiKey")
            return VoiceCaptureResult.Failed(VoiceCaptureFailure.MissingApiKey)
        }

        val selectedModelId = settings.selectedAsrModelId.trim()
        if (selectedModelId !in AsrModel.builtInIds) {
            debugLogger.debug("voice.remoteAsr.unsupportedModel model=$selectedModelId")
            return VoiceCaptureResult.Failed(VoiceCaptureFailure.UnsupportedAsrModel(selectedModelId))
        }

        val languageCode = asrLanguageProvider.languageCode().trim().ifBlank { "en" }
        val modelId = AsrModel.resolveForLanguage(selectedModelId, languageCode)

        debugLogger.debug(
            "voice.remoteAsr.request model=$modelId selectedModel=$selectedModelId " +
                "language=$languageCode audioBytes=${audio.bytes.size}",
        )
        return when (
            val asrResult = remoteAsrTranscriber.transcribe(
                VoiceAsrRequest(
                    apiKey = apiKey,
                    modelId = modelId,
                    languageCode = languageCode,
                    audio = audio,
                ),
            )
        ) {
            is VoiceAsrResult.Transcript -> {
                debugLogger.debug("voice.remoteAsr.transcript chars=${asrResult.text.trim().length}")
                enqueueTranscriptOrFail(asrResult.text)
            }
            is VoiceAsrResult.Failed -> {
                debugLogger.debug("voice.remoteAsr.failed reason=${asrResult.reason}")
                VoiceCaptureResult.Failed(VoiceCaptureFailure.AsrFailed(asrResult.reason))
            }
        }
    }

    private fun enqueueTranscriptOrFail(transcript: String): VoiceCaptureResult {
        val normalizedTranscript = transcript.trim()
        if (normalizedTranscript.isEmpty()) {
            debugLogger.debug("voice.transcript.blank")
            return VoiceCaptureResult.Failed(VoiceCaptureFailure.BlankTranscript)
        }

        aiCreateEnqueuer.enqueue(normalizedTranscript)
        debugLogger.debug("voice.transcript.enqueued chars=${normalizedTranscript.length}")
        return VoiceCaptureResult.Enqueued(normalizedTranscript)
    }

    private data class ActiveVoiceCapture(
        val recording: ActiveTemporaryAudioRecording,
        val systemSpeechRecognition: ActiveSystemSpeechRecognition?,
    )
}
