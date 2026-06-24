package com.cory.noter.voice

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
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
}
