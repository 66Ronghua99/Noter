package com.cory.noter

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cory.noter.ui.ringing.RingingScreen
import com.cory.noter.ui.ringing.RingingScreenTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RingingScreenSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ringing_screen_shows_themed_stop_surface() {
        var stopCount = 0

        composeRule.setContent {
            MaterialTheme {
                RingingScreen(
                    title = "Wake up",
                    onStop = { stopCount += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(RingingScreenTestTags.AlarmVisual).assertIsDisplayed()
        composeRule.onNodeWithText("Wake up").assertIsDisplayed()
        composeRule.onNodeWithText("Alarm is ringing").assertIsDisplayed()
        composeRule.onNodeWithTag(RingingScreenTestTags.StopAction)
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, stopCount)
    }
}
