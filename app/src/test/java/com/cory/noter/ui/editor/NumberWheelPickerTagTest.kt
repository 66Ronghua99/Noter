package com.cory.noter.ui.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NumberWheelPickerTagTest {
    @Test
    fun `selection frame tag is derived from wheel prefix`() {
        assertThat(wheelSelectionFrameTag("HourWheel")).isEqualTo("HourWheelSelectionFrame")
        assertThat(wheelSelectionFrameTag("IntervalWeeksWheel")).isEqualTo(
            "IntervalWeeksWheelSelectionFrame",
        )
    }
}
