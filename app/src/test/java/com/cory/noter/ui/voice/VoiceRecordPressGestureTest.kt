package com.cory.noter.ui.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceRecordPressGestureTest {
    @Test
    fun `released gesture invokes release callback only`() {
        var releases = 0
        var cancellations = 0

        handleRecordPressCompletion(
            wasReleased = true,
            onRecordReleased = { releases += 1 },
            onRecordCancelled = { cancellations += 1 },
        )

        assertThat(releases).isEqualTo(1)
        assertThat(cancellations).isEqualTo(0)
    }

    @Test
    fun `cancelled gesture invokes cancel callback only`() {
        var releases = 0
        var cancellations = 0

        handleRecordPressCompletion(
            wasReleased = false,
            onRecordReleased = { releases += 1 },
            onRecordCancelled = { cancellations += 1 },
        )

        assertThat(releases).isEqualTo(0)
        assertThat(cancellations).isEqualTo(1)
    }

    @Test
    fun `record pointer input key ignores callback identity changes`() {
        val firstKey = voiceRecordPointerInputKey(
            onRecordPressed = {},
            onRecordReleased = {},
            onRecordCancelled = {},
        )
        val secondKey = voiceRecordPointerInputKey(
            onRecordPressed = {},
            onRecordReleased = {},
            onRecordCancelled = {},
        )

        assertThat(secondKey).isSameInstanceAs(firstKey)
    }
}
