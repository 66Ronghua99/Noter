package com.cory.noter.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.cory.noter.ai.AiCreateBackgroundScheduler
import java.io.File
import java.io.IOException
import java.util.Locale
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
        val file = File(handle.id)
        if (file.exists() && !file.delete()) {
            throw IOException("Failed to delete temporary voice audio: ${file.absolutePath}")
        }
    }
}

class AndroidVoiceCaptureDebugLogger(
    private val enabled: Boolean,
) : VoiceCaptureDebugLogger {
    override fun debug(message: String) {
        if (enabled) {
            Log.d(TAG, message)
        }
    }

    override fun warn(message: String, error: Throwable?) {
        if (enabled) {
            Log.w(TAG, message, error)
        }
    }

    private companion object {
        const val TAG = "NoterVoice"
    }
}

class AndroidVoiceAsrLanguageProvider(
    context: Context,
) : VoiceAsrLanguageProvider {
    private val applicationContext = context.applicationContext

    override fun languageCode(): String {
        val locale = applicationContext.resources.configuration.locales.firstOrNullCompat()
            ?: Locale.getDefault()
        return when (locale.language.lowercase(Locale.ROOT)) {
            "zh" -> "zh"
            else -> "en"
        }
    }
}

class AndroidTemporaryAudioRecorder internal constructor(
    context: Context,
    private val tempFileFactory: () -> File = {
        File.createTempFile("noter-voice-", ".m4a", context.cacheDir)
    },
    private val mediaRecorderFactory: (Context) -> VoiceMediaRecorder = { recorderContext ->
        AndroidVoiceMediaRecorder(newMediaRecorder(recorderContext))
    },
) : TemporaryAudioRecorder {
    private val applicationContext = context.applicationContext

    override suspend fun start(): VoiceRecordingStartResult {
        var file: File? = null
        var recorder: VoiceMediaRecorder? = null
        return try {
            file = tempFileFactory()
            recorder = mediaRecorderFactory(applicationContext)
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
        } catch (error: IOException) {
            recorder.releaseIgnoringFailure()
            file?.delete()
            VoiceRecordingStartResult.Failed("Audio recording failed: ${error.message.orEmpty()}")
        } catch (error: RuntimeException) {
            recorder.releaseIgnoringFailure()
            file?.delete()
            VoiceRecordingStartResult.Failed("Audio recording failed: ${error.message.orEmpty()}")
        }
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
    private val speechRecognizerFactory: (Context) -> VoiceSpeechRecognizer = { recognizerContext ->
        AndroidVoiceSpeechRecognizer(SpeechRecognizer.createSpeechRecognizer(recognizerContext))
    },
) : SystemSpeechRecognizer {
    private val applicationContext = context.applicationContext

    @SuppressLint("MissingPermission")
    override suspend fun start(): SystemSpeechStartResult {
        if (!SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
            return SystemSpeechStartResult.Failed("System speech recognition is unavailable.")
        }

        val recognizer = speechRecognizerFactory(applicationContext)
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
    private val recorder: VoiceMediaRecorder,
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
            val recordedBytes = file.readBytes()
            recorder.release()
            released = true
            VoiceRecordingStopResult.Recorded(
                RecordedVoiceAudio(
                    handle = handle,
                    bytes = recordedBytes,
                ),
            )
        } catch (error: IOException) {
            recorder.releaseIgnoringFailure()
            released = true
            VoiceRecordingStopResult.Failed("Audio recording failed: ${error.message.orEmpty()}")
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

internal interface VoiceMediaRecorder {
    fun setAudioSource(source: Int)

    fun setOutputFormat(format: Int)

    fun setAudioEncoder(encoder: Int)

    fun setOutputFile(path: String)

    @Throws(IOException::class)
    fun prepare()

    fun start()

    fun stop()

    fun release()
}

private class AndroidVoiceMediaRecorder(
    private val recorder: MediaRecorder,
) : VoiceMediaRecorder {
    override fun setAudioSource(source: Int) {
        recorder.setAudioSource(source)
    }

    override fun setOutputFormat(format: Int) {
        recorder.setOutputFormat(format)
    }

    override fun setAudioEncoder(encoder: Int) {
        recorder.setAudioEncoder(encoder)
    }

    override fun setOutputFile(path: String) {
        recorder.setOutputFile(path)
    }

    override fun prepare() {
        recorder.prepare()
    }

    override fun start() {
        recorder.start()
    }

    override fun stop() {
        recorder.stop()
    }

    override fun release() {
        recorder.release()
    }
}

private fun newMediaRecorder(context: Context): MediaRecorder =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }

internal class AndroidActiveSystemSpeechRecognition(
    private val recognizer: VoiceSpeechRecognizer,
    private val resultTimeoutMillis: Long,
) : ActiveSystemSpeechRecognition,
    RecognitionListener {
    private val result = CompletableDeferred<SystemSpeechResult>()
    private var destroyed = false

    override suspend fun stopAndTranscribe(): SystemSpeechResult {
        runCatching { recognizer.stopListening() }
        return try {
            withTimeoutOrNull(resultTimeoutMillis) { result.await() }
                ?: SystemSpeechResult.Failed("System speech recognition timed out.")
        } finally {
            destroy()
        }
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

interface VoiceSpeechRecognizer {
    fun setRecognitionListener(listener: RecognitionListener)

    fun startListening(intent: Intent)

    fun stopListening()

    fun cancel()

    fun destroy()
}

private class AndroidVoiceSpeechRecognizer(
    private val recognizer: SpeechRecognizer,
) : VoiceSpeechRecognizer {
    override fun setRecognitionListener(listener: RecognitionListener) {
        recognizer.setRecognitionListener(listener)
    }

    override fun startListening(intent: Intent) {
        recognizer.startListening(intent)
    }

    override fun stopListening() {
        recognizer.stopListening()
    }

    override fun cancel() {
        recognizer.cancel()
    }

    override fun destroy() {
        recognizer.destroy()
    }
}

private fun VoiceMediaRecorder?.releaseIgnoringFailure() {
    runCatching { this?.release() }
}

private fun LocaleList.firstOrNullCompat(): Locale? =
    if (isEmpty) null else get(0)
