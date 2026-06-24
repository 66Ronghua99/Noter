package com.cory.noter.voice

import android.content.Intent
import android.speech.RecognitionListener
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidTemporaryAudioRecorderTest {
    @Test
    fun `start maps prepare failure to explicit result and deletes allocated temp file`() = runTest {
        val tempFile = File.createTempFile("noter-test-voice-", ".m4a")
        val mediaRecorder = FailingVoiceMediaRecorder()
        val recorder = AndroidTemporaryAudioRecorder(
            context = ApplicationProvider.getApplicationContext(),
            tempFileFactory = { tempFile },
            mediaRecorderFactory = { mediaRecorder },
        )

        val result = recorder.start()

        assertThat(result).isInstanceOf(VoiceRecordingStartResult.Failed::class.java)
        assertThat((result as VoiceRecordingStartResult.Failed).reason).contains("prepare failed")
        assertThat(mediaRecorder.released).isTrue()
        assertThat(tempFile.exists()).isFalse()
    }

    @Test
    fun `cleanup reports failure when existing temp file delete returns false`() = runTest {
        val directory = createTempDirectory(prefix = "noter-test-voice-cleanup-").toFile()
        val child = File(directory, "still-present.txt")
        child.writeText("not deleted")
        val cleanup = FileTemporaryAudioCleanup()

        val result = runCatching {
            cleanup.cleanup(TemporaryAudioHandle(directory.absolutePath))
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
        assertThat(result.exceptionOrNull()!!.message).contains(directory.absolutePath)
        assertThat(directory.exists()).isTrue()

        child.toPath().deleteIfExists()
        directory.toPath().deleteIfExists()
    }

    @Test
    fun `stop maps recorded audio read failure to explicit result`() = runTest {
        val tempFile = File.createTempFile("noter-test-voice-read-", ".m4a")
        val mediaRecorder = RecordingVoiceMediaRecorder()
        val recorder = AndroidTemporaryAudioRecorder(
            context = ApplicationProvider.getApplicationContext(),
            tempFileFactory = { tempFile },
            mediaRecorderFactory = { mediaRecorder },
        )

        val startResult = recorder.start()
        assertThat(startResult).isInstanceOf(VoiceRecordingStartResult.Started::class.java)
        assertThat(tempFile.delete()).isTrue()
        val stopResult = (startResult as VoiceRecordingStartResult.Started).recording.stop()

        assertThat(stopResult).isInstanceOf(VoiceRecordingStopResult.Failed::class.java)
        assertThat((stopResult as VoiceRecordingStopResult.Failed).reason).contains("Audio recording failed")
        assertThat(mediaRecorder.released).isTrue()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `system speech recognition destroys recognizer when transcription await is cancelled`() = runTest {
        val recognizer = RecordingSpeechRecognizer()
        val recognition = AndroidActiveSystemSpeechRecognition(
            recognizer = recognizer,
            resultTimeoutMillis = 10_000L,
        )

        val job = async { recognition.stopAndTranscribe() }
        runCurrent()
        assertThat(recognizer.stopListeningCalls).isEqualTo(1)
        job.cancelAndJoin()

        assertThat(recognizer.destroyCalls).isEqualTo(1)
    }

    private class FailingVoiceMediaRecorder : VoiceMediaRecorder {
        var released = false

        override fun setAudioSource(source: Int) = Unit

        override fun setOutputFormat(format: Int) = Unit

        override fun setAudioEncoder(encoder: Int) = Unit

        override fun setOutputFile(path: String) = Unit

        override fun prepare() {
            throw IOException("prepare failed")
        }

        override fun start() = Unit

        override fun stop() = Unit

        override fun release() {
            released = true
        }
    }

    private class RecordingVoiceMediaRecorder : VoiceMediaRecorder {
        var released = false

        override fun setAudioSource(source: Int) = Unit

        override fun setOutputFormat(format: Int) = Unit

        override fun setAudioEncoder(encoder: Int) = Unit

        override fun setOutputFile(path: String) = Unit

        override fun prepare() = Unit

        override fun start() = Unit

        override fun stop() = Unit

        override fun release() {
            released = true
        }
    }

    private class RecordingSpeechRecognizer : VoiceSpeechRecognizer {
        var stopListeningCalls = 0
        var destroyCalls = 0

        override fun setRecognitionListener(listener: RecognitionListener) = Unit

        override fun startListening(intent: Intent) = Unit

        override fun stopListening() {
            stopListeningCalls += 1
        }

        override fun cancel() = Unit

        override fun destroy() {
            destroyCalls += 1
        }
    }
}
