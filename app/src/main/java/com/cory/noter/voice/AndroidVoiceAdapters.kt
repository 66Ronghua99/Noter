package com.cory.noter.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.cory.noter.ai.AiCreateBackgroundScheduler
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

class AndroidMicrophonePermissionChecker(
    context: Context,
) : MicrophonePermissionChecker {
    private val applicationContext = context.applicationContext

    override fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
}

class BackgroundVoiceAiCreateEnqueuer(
    private val scheduler: AiCreateBackgroundScheduler,
) : VoiceAiCreateEnqueuer {
    override fun enqueue(transcript: String) {
        scheduler.enqueue(transcript)
    }
}

class FileTemporaryAudioCleanup : TemporaryAudioCleanup {
    override suspend fun cleanup(handle: TemporaryAudioHandle) {
        File(handle.id).delete()
    }
}

class AndroidTemporaryAudioRecorder(
    context: Context,
    private val tempFileFactory: () -> File = {
        File.createTempFile("noter-voice-", ".m4a", context.cacheDir)
    },
) : TemporaryAudioRecorder {
    private val applicationContext = context.applicationContext

    override suspend fun start(): VoiceRecordingStartResult {
        val file = tempFileFactory()
        val recorder = newMediaRecorder(applicationContext)
        return try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            VoiceRecordingStartResult.Started(
                AndroidActiveTemporaryAudioRecording(
                    recorder = recorder,
                    file = file,
                ),
            )
        } catch (error: RuntimeException) {
            recorder.releaseIgnoringFailure()
            file.delete()
            VoiceRecordingStartResult.Failed("Audio recording failed: ${error.message.orEmpty()}")
        }
    }

    private fun newMediaRecorder(context: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
}

class AndroidSystemSpeechRecognizer(
    context: Context,
    private val intentFactory: () -> Intent = {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
    },
    private val resultTimeoutMillis: Long = 10_000L,
) : SystemSpeechRecognizer {
    private val applicationContext = context.applicationContext

    @SuppressLint("MissingPermission")
    override suspend fun start(): SystemSpeechStartResult {
        if (!SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
            return SystemSpeechStartResult.Failed("System speech recognition is unavailable.")
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
        val session = AndroidActiveSystemSpeechRecognition(
            recognizer = recognizer,
            resultTimeoutMillis = resultTimeoutMillis,
        )
        recognizer.setRecognitionListener(session)
        return try {
            recognizer.startListening(intentFactory())
            SystemSpeechStartResult.Started(session)
        } catch (error: RuntimeException) {
            recognizer.destroy()
            SystemSpeechStartResult.Failed("System speech recognition failed to start: ${error.message.orEmpty()}")
        }
    }
}

private class AndroidActiveTemporaryAudioRecording(
    private val recorder: MediaRecorder,
    private val file: File,
) : ActiveTemporaryAudioRecording {
    override val handle: TemporaryAudioHandle = TemporaryAudioHandle(file.absolutePath)
    private var released = false

    override suspend fun stop(): VoiceRecordingStopResult {
        if (released) {
            return VoiceRecordingStopResult.Failed("Audio recording is already closed.")
        }

        return try {
            recorder.stop()
            recorder.release()
            released = true
            VoiceRecordingStopResult.Recorded(
                RecordedVoiceAudio(
                    handle = handle,
                    bytes = file.readBytes(),
                ),
            )
        } catch (error: RuntimeException) {
            recorder.releaseIgnoringFailure()
            released = true
            VoiceRecordingStopResult.Failed("Audio recording failed: ${error.message.orEmpty()}")
        }
    }

    override suspend fun cancel(): VoiceRecordingStopResult {
        if (!released) {
            runCatching { recorder.stop() }
            recorder.releaseIgnoringFailure()
            released = true
        }
        return VoiceRecordingStopResult.Cancelled
    }
}

private class AndroidActiveSystemSpeechRecognition(
    private val recognizer: SpeechRecognizer,
    private val resultTimeoutMillis: Long,
) : ActiveSystemSpeechRecognition,
    RecognitionListener {
    private val result = CompletableDeferred<SystemSpeechResult>()
    private var destroyed = false

    override suspend fun stopAndTranscribe(): SystemSpeechResult {
        runCatching { recognizer.stopListening() }
        val speechResult = withTimeoutOrNull(resultTimeoutMillis) { result.await() }
            ?: SystemSpeechResult.Failed("System speech recognition timed out.")
        destroy()
        return speechResult
    }

    override suspend fun cancel() {
        if (!result.isCompleted) {
            result.complete(SystemSpeechResult.Failed("System speech recognition cancelled."))
        }
        runCatching { recognizer.cancel() }
        destroy()
    }

    override fun onResults(results: Bundle?) {
        val transcript = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        result.complete(SystemSpeechResult.Transcript(transcript))
    }

    override fun onError(error: Int) {
        result.complete(SystemSpeechResult.Failed("System speech recognition failed: $error"))
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() = Unit

    override fun onPartialResults(partialResults: Bundle?) = Unit

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun destroy() {
        if (!destroyed) {
            recognizer.destroy()
            destroyed = true
        }
    }
}

private fun MediaRecorder.releaseIgnoringFailure() {
    runCatching { release() }
}
