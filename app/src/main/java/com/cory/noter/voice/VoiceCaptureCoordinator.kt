package com.cory.noter.voice

import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException

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

sealed interface VoiceAsrResult {
    data class Transcript(val text: String) : VoiceAsrResult

    data class Failed(val failure: VoiceCaptureFailure) : VoiceAsrResult
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

    data object ServiceUnavailable : VoiceCaptureFailure

    data object UpdateRequired : VoiceCaptureFailure

    data object UploadTooLarge : VoiceCaptureFailure

    data class RecordingFailed(val reason: String) : VoiceCaptureFailure

    data class AsrFailed(val reason: String) : VoiceCaptureFailure
}

class VoiceCaptureCoordinator(
    private val temporaryAudioRecorder: TemporaryAudioRecorder,
    private val remoteAsrTranscriber: RemoteAsrTranscriber,
    private val temporaryAudioCleanup: TemporaryAudioCleanup,
    private val aiCreateEnqueuer: VoiceAiCreateEnqueuer,
    private val asrLanguageProvider: VoiceAsrLanguageProvider,
    private val debugLogger: VoiceCaptureDebugLogger = VoiceCaptureDebugLogger.None,
) : VoiceCaptureController {
    private val logger = Logger.getLogger(VoiceCaptureCoordinator::class.java.name)
    private var activeCapture: ActiveTemporaryAudioRecording? = null

    override suspend fun start(): VoiceCaptureResult {
        if (activeCapture != null) {
            return VoiceCaptureResult.Failed(VoiceCaptureFailure.AlreadyRecording)
        }
        val recording = when (val result = temporaryAudioRecorder.start()) {
            is VoiceRecordingStartResult.Started -> result.recording
            is VoiceRecordingStartResult.Failed -> {
                debugLogger.debug("voice.recording.start.failed")
                return VoiceCaptureResult.Failed(VoiceCaptureFailure.RecordingFailed(result.reason))
            }
        }
        activeCapture = recording
        debugLogger.debug("voice.recording.started handle=${recording.handle.id}")
        return VoiceCaptureResult.RecordingStarted
    }

    override suspend fun release(): VoiceCaptureResult {
        val recording = activeCapture ?: return VoiceCaptureResult.Failed(VoiceCaptureFailure.NoActiveRecording)
        activeCapture = null
        return when (val result = recording.stop()) {
            is VoiceRecordingStopResult.Recorded -> processRecordedAudio(result.audio)
            is VoiceRecordingStopResult.Failed -> {
                cleanupWithoutMasking(recording.handle)
                VoiceCaptureResult.Failed(VoiceCaptureFailure.RecordingFailed(result.reason))
            }
            VoiceRecordingStopResult.Cancelled -> {
                cleanupWithoutMasking(recording.handle)
                VoiceCaptureResult.Cancelled
            }
        }
    }

    override suspend fun cancel(): VoiceCaptureResult {
        val recording = activeCapture ?: return VoiceCaptureResult.Failed(VoiceCaptureFailure.NoActiveRecording)
        activeCapture = null
        recording.cancel()
        cleanupWithoutMasking(recording.handle)
        return VoiceCaptureResult.Cancelled
    }

    private suspend fun processRecordedAudio(audio: RecordedVoiceAudio): VoiceCaptureResult {
        return try {
            val languageCode = asrLanguageProvider.languageCode().trim().ifBlank { "en" }
            when (val result = remoteAsrTranscriber.transcribe(VoiceAsrRequest(languageCode, audio))) {
                is VoiceAsrResult.Transcript -> enqueueTranscriptOrFail(result.text)
                is VoiceAsrResult.Failed -> VoiceCaptureResult.Failed(result.failure)
            }
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
            logger.log(Level.WARNING, "Temporary voice audio cleanup failed.", error)
        }
    }

    private fun enqueueTranscriptOrFail(transcript: String): VoiceCaptureResult {
        val normalized = transcript.trim()
        if (normalized.isEmpty()) {
            return VoiceCaptureResult.Failed(VoiceCaptureFailure.BlankTranscript)
        }
        aiCreateEnqueuer.enqueue(normalized)
        return VoiceCaptureResult.Enqueued(normalized)
    }
}
