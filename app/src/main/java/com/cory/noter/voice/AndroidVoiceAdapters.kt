package com.cory.noter.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.core.content.ContextCompat
import com.cory.noter.ai.AiCreateBackgroundScheduler
import java.io.File
import java.io.IOException
import java.util.Locale

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
            throw IOException("Failed to delete temporary voice audio.")
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
            VoiceRecordingStartResult.Failed("Audio recording failed.")
        } catch (error: RuntimeException) {
            recorder.releaseIgnoringFailure()
            file?.delete()
            VoiceRecordingStartResult.Failed("Audio recording failed.")
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
            VoiceRecordingStopResult.Recorded(RecordedVoiceAudio(handle, recordedBytes))
        } catch (error: IOException) {
            recorder.releaseIgnoringFailure()
            released = true
            VoiceRecordingStopResult.Failed("Audio recording failed.")
        } catch (error: RuntimeException) {
            recorder.releaseIgnoringFailure()
            released = true
            VoiceRecordingStopResult.Failed("Audio recording failed.")
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
    override fun setAudioSource(source: Int) = recorder.setAudioSource(source)

    override fun setOutputFormat(format: Int) = recorder.setOutputFormat(format)

    override fun setAudioEncoder(encoder: Int) = recorder.setAudioEncoder(encoder)

    override fun setOutputFile(path: String) = recorder.setOutputFile(path)

    override fun prepare() = recorder.prepare()

    override fun start() = recorder.start()

    override fun stop() = recorder.stop()

    override fun release() = recorder.release()
}

private fun newMediaRecorder(context: Context): MediaRecorder =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }

private fun VoiceMediaRecorder?.releaseIgnoringFailure() {
    runCatching { this?.release() }
}

private fun LocaleList.firstOrNullCompat(): Locale? = if (isEmpty) null else get(0)
