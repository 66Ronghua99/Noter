package com.cory.noter

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import com.cory.noter.ui.NoterApp
import com.cory.noter.ui.ai.AiCreateTestTags
import com.cory.noter.ui.ai.AiCreateUiState
import com.cory.noter.ui.ai.TextModeContent
import com.cory.noter.ui.ai.UnifiedAiCreateScreen
import com.cory.noter.ui.ai.UnifiedAiCreateTestTags
import com.cory.noter.ui.alarm_list.AlarmListScreen
import com.cory.noter.ui.alarm_list.AlarmListTestTags
import com.cory.noter.ui.alarm_list.AlarmListUiState
import com.cory.noter.ui.text.UiText
import com.cory.noter.ui.voice.VoiceHomeScreen
import com.cory.noter.ui.voice.VoiceHomeStatus
import com.cory.noter.ui.voice.VoiceHomeTestTags
import com.cory.noter.ui.voice.VoiceHomeUiState
import com.cory.noter.ui.voice.VoiceModeContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VoiceHomeSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun noter_app_default_start_destination_is_unified_voice_mode() {
        composeRule.setContent {
            MaterialTheme {
                TestNoterApp()
            }
        }

        composeRule.onNodeWithTag(UnifiedAiCreateTestTags.Root)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(UnifiedAiCreateTestTags.VoiceModeAction)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(VoiceHomeTestTags.RecordButton)
            .assertIsDisplayed()
    }

    @Test
    fun noter_app_unified_create_switches_to_text_and_back_to_voice() {
        composeRule.setContent {
            MaterialTheme {
                TestNoterApp()
            }
        }

        composeRule.onNodeWithTag(UnifiedAiCreateTestTags.TextModeAction)
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag(AppRouteTestTags.AiCreate)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(AiCreateTestTags.PromptInput)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(AiCreateTestTags.SubmitAction)
            .assertIsDisplayed()

        composeRule.onNodeWithTag(UnifiedAiCreateTestTags.VoiceModeAction)
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag(VoiceHomeTestTags.RecordButton)
            .assertIsDisplayed()
    }

    @Test
    fun noter_app_unified_text_mode_exposes_prompt_entry_and_submit_action() {
        var prompt = ""
        var submitCount = 0

        composeRule.setContent {
            MaterialTheme {
                TestNoterApp(
                    textState = AiCreateUiState(
                        selectedModelId = "demo-model",
                        prompt = prompt,
                    ),
                    onPromptChanged = { prompt = it },
                    onSubmit = { submitCount += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(UnifiedAiCreateTestTags.TextModeAction)
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag(AiCreateTestTags.Root)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(AiCreateTestTags.PromptInput)
            .assertIsDisplayed()
            .performTextInput("Wake me at seven tomorrow")
        composeRule.onNodeWithTag(AiCreateTestTags.SubmitAction)
            .assertIsDisplayed()
            .performClick()

        assertEquals("Wake me at seven tomorrow", prompt)
        assertEquals(1, submitCount)
    }

    @Test
    fun noter_app_unified_text_mode_exposes_status_error_loading_and_exact_alarm_actions() {
        var openedExactAlarmSettings = 0

        composeRule.setContent {
            MaterialTheme {
                TestNoterApp(
                    textState = AiCreateUiState(
                        selectedModelId = "demo-model",
                        isLoading = true,
                        statusMessage = UiText.Raw("Creating alarm..."),
                        errorMessage = UiText.Raw("Exact alarm permission is required."),
                        exactAlarmPermissionRequired = true,
                    ),
                    onOpenExactAlarmSettings = { openedExactAlarmSettings += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(UnifiedAiCreateTestTags.TextModeAction)
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag(AiCreateTestTags.LoadingIndicator)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(AiCreateTestTags.StatusMessage)
            .assertIsDisplayed()
            .assertTextContains("Creating alarm...")
        composeRule.onNodeWithTag(AiCreateTestTags.ErrorMessage)
            .assertIsDisplayed()
            .assertTextContains("Exact alarm permission is required.")
        composeRule.onNodeWithTag(AiCreateTestTags.ExactAlarmAction)
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, openedExactAlarmSettings)
    }

    @Test
    fun noter_app_create_list_bottom_tabs_navigate_between_routes() {
        composeRule.setContent {
            MaterialTheme {
                TestNoterApp()
            }
        }

        composeRule.onNodeWithTag(UnifiedAiCreateTestTags.ListTabAction)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(AppRouteTestTags.AlarmList)
            .assertIsDisplayed()

        composeRule.onNodeWithTag(AlarmListTestTags.CreateTabAction)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(UnifiedAiCreateTestTags.Root)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(VoiceHomeTestTags.RecordButton)
            .assertIsDisplayed()
    }

    @Test
    fun noter_app_unified_manual_create_fab_opens_editor() {
        var openedManualCreate = 0

        composeRule.setContent {
            MaterialTheme {
                TestNoterApp(onOpenManualCreate = { openedManualCreate += 1 })
            }
        }

        composeRule.onNodeWithTag(UnifiedAiCreateTestTags.ManualCreateFabAction)
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, openedManualCreate)
    }

    @Test
    fun noter_app_voice_home_settings_action_reaches_settings() {
        composeRule.setContent {
            MaterialTheme {
                TestNoterApp()
            }
        }

        composeRule.onNodeWithTag(UnifiedAiCreateTestTags.SettingsAction)
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag(AppRouteTestTags.Settings)
            .assertIsDisplayed()
    }

    @Test
    fun noter_app_voice_home_text_fallback_switches_to_text_mode_in_place() {
        composeRule.setContent {
            MaterialTheme {
                TestNoterApp(
                    voiceState = VoiceHomeUiState(
                        status = VoiceHomeStatus.Idle,
                        errorMessage = UiText.Raw("Speech recognition failed."),
                        showTextFallbackAction = true,
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(VoiceHomeTestTags.TextFallbackAction)
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag(AppRouteTestTags.AiCreate)
            .assertIsDisplayed()
    }

    @Test
    fun voice_home_idle_surface_exposes_record_list_and_settings_actions() {
        var pressed = 0
        var released = 0
        var openedList = 0
        var openedSettings = 0
        var openedPermissionSettings = 0

        composeRule.setContent {
            MaterialTheme {
                VoiceHomeScreen(
                    state = VoiceHomeUiState(status = VoiceHomeStatus.Idle),
                    onRecordPressed = { pressed += 1 },
                    onRecordReleased = { released += 1 },
                    onRecordCancelled = {},
                    onRetry = {},
                    onOpenPermissionSettings = { openedPermissionSettings += 1 },
                    onOpenTextInput = {},
                    onOpenAlarmList = { openedList += 1 },
                    onOpenSettings = { openedSettings += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(VoiceHomeTestTags.RecordButton)
            .assertIsDisplayed()
            .performTouchInput {
                down(center)
                up()
            }
        composeRule.onNodeWithTag(VoiceHomeTestTags.ListAction).performClick()
        composeRule.onNodeWithTag(VoiceHomeTestTags.SettingsAction).performClick()

        assertEquals(1, pressed)
        assertEquals(1, released)
        assertEquals(1, openedList)
        assertEquals(1, openedSettings)
        assertEquals(0, openedPermissionSettings)
    }

    @Test
    fun voice_home_cancelled_record_press_invokes_cancel_without_release() {
        var pressed = 0
        var released = 0
        var cancelled = 0

        composeRule.setContent {
            MaterialTheme {
                VoiceHomeScreen(
                    state = VoiceHomeUiState(status = VoiceHomeStatus.Idle),
                    onRecordPressed = { pressed += 1 },
                    onRecordReleased = { released += 1 },
                    onRecordCancelled = { cancelled += 1 },
                    onRetry = {},
                    onOpenPermissionSettings = {},
                    onOpenTextInput = {},
                    onOpenAlarmList = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag(VoiceHomeTestTags.RecordButton)
            .assertIsDisplayed()
            .performTouchInput {
                down(center)
                cancel()
            }

        assertEquals(1, pressed)
        assertEquals(0, released)
        assertEquals(1, cancelled)
    }

    @Test
    fun voice_home_active_record_press_survives_recording_recomposition() {
        var pressed = 0
        var released = 0
        var cancelled = 0

        composeRule.setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(VoiceHomeUiState(status = VoiceHomeStatus.Idle)) }
                VoiceHomeScreen(
                    state = state,
                    onRecordPressed = {
                        pressed += 1
                        state = VoiceHomeUiState(status = VoiceHomeStatus.Recording)
                    },
                    onRecordReleased = { released += 1 },
                    onRecordCancelled = { cancelled += 1 },
                    onRetry = {},
                    onOpenPermissionSettings = {},
                    onOpenTextInput = {},
                    onOpenAlarmList = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag(VoiceHomeTestTags.RecordButton)
            .assertIsDisplayed()
            .performTouchInput {
                down(center)
            }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VoiceHomeTestTags.RecordButton)
            .performTouchInput {
                up()
            }

        assertEquals(1, pressed)
        assertEquals(1, released)
        assertEquals(0, cancelled)
    }

    @Test
    fun voice_home_recording_surface_exposes_cancel_action() {
        var cancelled = 0

        composeRule.setContent {
            MaterialTheme {
                VoiceHomeScreen(
                    state = VoiceHomeUiState(status = VoiceHomeStatus.Recording),
                    onRecordPressed = {},
                    onRecordReleased = {},
                    onRecordCancelled = { cancelled += 1 },
                    onRetry = {},
                    onOpenPermissionSettings = {},
                    onOpenTextInput = {},
                    onOpenAlarmList = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag(VoiceHomeTestTags.CancelAction)
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, cancelled)
    }

    @Test
    fun voice_home_notice_surface_is_visible_without_transcript_text() {
        composeRule.setContent {
            MaterialTheme {
                VoiceHomeScreen(
                    state = VoiceHomeUiState(
                        status = VoiceHomeStatus.Idle,
                        noticeMessage = UiText.Raw("Noter is processing your alarm."),
                    ),
                    onRecordPressed = {},
                    onRecordReleased = {},
                    onRecordCancelled = {},
                    onRetry = {},
                    onOpenPermissionSettings = {},
                    onOpenTextInput = {},
                    onOpenAlarmList = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag(VoiceHomeTestTags.Notice)
            .assertIsDisplayed()
            .assertTextContains("Noter is processing your alarm.")
    }

    @Test
    fun voice_home_failure_surface_exposes_retry_and_text_fallback_actions() {
        var retries = 0
        var textFallbacks = 0
        var permissionRecoveries = 0

        composeRule.setContent {
            MaterialTheme {
                VoiceHomeScreen(
                    state = VoiceHomeUiState(
                        status = VoiceHomeStatus.Idle,
                        errorMessage = UiText.Raw("Speech recognition failed."),
                        showRetryAction = true,
                        showTextFallbackAction = true,
                    ),
                    onRecordPressed = {},
                    onRecordReleased = {},
                    onRecordCancelled = {},
                    onRetry = { retries += 1 },
                    onOpenPermissionSettings = { permissionRecoveries += 1 },
                    onOpenTextInput = { textFallbacks += 1 },
                    onOpenAlarmList = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag(VoiceHomeTestTags.RetryAction)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(VoiceHomeTestTags.TextFallbackAction)
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, retries)
        assertEquals(1, textFallbacks)
        assertEquals(0, permissionRecoveries)
    }

    @Test
    fun voice_home_permission_surface_exposes_permission_recovery_action() {
        var permissionRecoveries = 0

        composeRule.setContent {
            MaterialTheme {
                VoiceHomeScreen(
                    state = VoiceHomeUiState(
                        status = VoiceHomeStatus.PermissionNeeded,
                        errorMessage = UiText.Raw("Microphone permission is needed."),
                        showPermissionRecoveryAction = true,
                        showTextFallbackAction = true,
                    ),
                    onRecordPressed = {},
                    onRecordReleased = {},
                    onRecordCancelled = {},
                    onRetry = {},
                    onOpenPermissionSettings = { permissionRecoveries += 1 },
                    onOpenTextInput = {},
                    onOpenAlarmList = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag(VoiceHomeTestTags.PermissionRecoveryAction)
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, permissionRecoveries)
    }

    @Composable
    private fun TestNoterApp(
        voiceState: VoiceHomeUiState = VoiceHomeUiState(status = VoiceHomeStatus.Idle),
        textState: AiCreateUiState = AiCreateUiState(selectedModelId = "demo-model"),
        onPromptChanged: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
        onOpenExactAlarmSettings: () -> Unit = {},
        onOpenManualCreate: () -> Unit = {},
    ) {
        NoterApp(
            unifiedAiCreateScreen = { onOpenAlarmList, onOpenSettings, _ ->
                UnifiedAiCreateScreen(
                    voiceContent = { onSwitchToText ->
                        VoiceModeContent(
                            state = voiceState,
                            onRecordPressed = {},
                            onRecordReleased = {},
                            onRecordCancelled = {},
                            onRetry = {},
                            onOpenPermissionSettings = {},
                            onOpenTextInput = onSwitchToText,
                        )
                    },
                    textContent = {
                        Box(modifier = Modifier.testTag(AppRouteTestTags.AiCreate))
                        TextModeContent(
                            state = textState,
                            onPromptChanged = onPromptChanged,
                            onSubmit = onSubmit,
                            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                            onOpenManualCreate = onOpenManualCreate,
                        )
                    },
                    onOpenAlarmList = onOpenAlarmList,
                    onOpenSettings = onOpenSettings,
                    onOpenManualCreate = onOpenManualCreate,
                )
            },
            alarmListScreen = { _, onOpenAiCreate, _, _ ->
                AlarmListScreen(
                    state = AlarmListUiState(),
                    onAlarmEnabledChanged = { _, _ -> },
                    onEditAlarm = {},
                    onDeleteAlarm = {},
                    onOpenSettings = {},
                    onOpenManualCreate = {},
                    onOpenAiCreate = onOpenAiCreate,
                    modifier = Modifier.testTag(AppRouteTestTags.AlarmList),
                )
            },
            alarmEditorScreen = { _, _ ->
                Box(modifier = Modifier.testTag(AppRouteTestTags.Editor))
            },
            settingsScreen = { _, _, _, _, _, _ ->
                Box(modifier = Modifier.testTag(AppRouteTestTags.Settings))
            },
            appearanceSettingsScreen = { _, _ ->
                Box(modifier = Modifier.testTag(AppRouteTestTags.Settings))
            },
            aiVoiceSettingsScreen = { _, _ ->
                Box(modifier = Modifier.testTag(AppRouteTestTags.Settings))
            },
            soundSettingsScreen = { _, _ ->
                Box(modifier = Modifier.testTag(AppRouteTestTags.Settings))
            },
            permissionsSettingsScreen = { _, _ ->
                Box(modifier = Modifier.testTag(AppRouteTestTags.Settings))
            },
        )
    }

    private object AppRouteTestTags {
        const val AlarmList = "NoterAppAlarmListRoute"
        const val Editor = "NoterAppEditorRoute"
        const val AiCreate = "NoterAppAiCreateRoute"
        const val Settings = "NoterAppSettingsRoute"
    }
}
