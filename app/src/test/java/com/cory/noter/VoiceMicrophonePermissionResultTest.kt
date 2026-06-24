package com.cory.noter

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceMicrophonePermissionResultTest {
    @Test
    fun `granted permission after press ended waits for a new press`() {
        val action = resolveMicrophonePermissionResult(
            granted = true,
            recordPressActive = false,
        )

        assertThat(action).isEqualTo(VoiceMicrophonePermissionAction.WaitForNewPress)
    }

    @Test
    fun `granted permission during active press starts capture`() {
        val action = resolveMicrophonePermissionResult(
            granted = true,
            recordPressActive = true,
        )

        assertThat(action).isEqualTo(VoiceMicrophonePermissionAction.StartCapture)
    }

    @Test
    fun `denied permission forwards to permission needed path`() {
        val action = resolveMicrophonePermissionResult(
            granted = false,
            recordPressActive = false,
        )

        assertThat(action).isEqualTo(VoiceMicrophonePermissionAction.ShowPermissionNeeded)
    }

    @Test
    fun `permission result route has no release action`() {
        assertThat(VoiceMicrophonePermissionAction.entries.map { it.name })
            .doesNotContain("ReleaseCapture")
    }
}
