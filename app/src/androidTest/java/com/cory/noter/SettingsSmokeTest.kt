package com.cory.noter

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cory.noter.ui.NoterApp
import com.cory.noter.ui.Routes
import com.cory.noter.ui.settings.AppearanceSettingsScreen
import com.cory.noter.ui.settings.CalendarSettingsScreen
import com.cory.noter.ui.settings.PermissionGuidanceUiModel
import com.cory.noter.ui.settings.PermissionsSettingsScreen
import com.cory.noter.ui.settings.SettingsDirectoryRowUiModel
import com.cory.noter.ui.settings.SettingsScreen
import com.cory.noter.ui.settings.SettingsTestTags
import com.cory.noter.ui.settings.SettingsUiState
import com.cory.noter.ui.settings.SoundSettingsScreen
import com.cory.noter.ui.text.UiText
import org.junit.Rule
import org.junit.Test

class SettingsSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settings_home_exposes_only_retained_destinations() {
        setSettingsRouteTestContent()

        composeRule.onNodeWithTag(SettingsTestTags.Home).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.AppearanceRow).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SoundRow).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.CalendarRow).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.PermissionsRow).assertIsDisplayed()
        composeRule.onAllNodesWithText("OpenRouter").assertCountEquals(0)
    }

    @Test
    fun retained_settings_destinations_navigate() {
        setSettingsRouteTestContent()
        composeRule.onNodeWithTag(SettingsTestTags.AppearanceRow).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.AppearanceDetail).assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag(SettingsTestTags.SoundRow).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SoundDetail).assertIsDisplayed()
    }

    @Test
    fun calendar_and_permission_settings_remain_usable() {
        setSettingsRouteTestContent()
        composeRule.onNodeWithTag(SettingsTestTags.CalendarRow).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.CalendarDetail).assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag(SettingsTestTags.PermissionsRow).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.PermissionsDetail).assertIsDisplayed()
    }

    private fun setSettingsRouteTestContent() {
        composeRule.setContent {
            MaterialTheme {
                NoterApp(
                    unifiedAiCreateScreen = { _, _, _ -> Box(Modifier.testTag("ai-create")) },
                    alarmListScreen = { _, _, _, _ -> Box(Modifier.testTag("alarms")) },
                    alarmEditorScreen = { _, _, _ -> Box(Modifier.testTag("editor")) },
                    settingsScreen = { onOpenAppearance, onOpenSound, onOpenCalendar, onOpenPermissions, _, _ ->
                        SettingsScreen(
                            state = previewState,
                            onOpenAppearance = onOpenAppearance,
                            onOpenSound = onOpenSound,
                            onOpenCalendar = onOpenCalendar,
                            onOpenPermissions = onOpenPermissions,
                            onBack = {},
                        )
                    },
                    appearanceSettingsScreen = { _, _ ->
                        AppearanceSettingsScreen(
                            state = previewState,
                            onThemePresetSelected = {},
                            onCustomThemeSeedColorChanged = {},
                            onCustomThemeSeedColorCommitted = {},
                            onBack = {},
                        )
                    },
                    soundSettingsScreen = { _, _ ->
                        SoundSettingsScreen(
                            state = previewState,
                            onPickDefaultRingtone = {},
                            onBack = {},
                        )
                    },
                    calendarSettingsScreen = { _, _ ->
                        CalendarSettingsScreen(
                            state = previewState,
                            onCalendarSelected = {},
                            onClearCalendar = {},
                            onCalendarPermissionAction = {},
                            onBack = {},
                        )
                    },
                    permissionsSettingsScreen = { _, _ ->
                        PermissionsSettingsScreen(
                            state = previewState,
                            onPermissionAction = {},
                            onBack = {},
                        )
                    },
                    startDestination = Routes.SETTINGS,
                )
            }
        }
    }

    private companion object {
        val previewState = SettingsUiState(
            defaultRingtoneUri = "content://ringtone/demo",
            directoryRows = listOf(
                SettingsDirectoryRowUiModel(
                    id = "appearance",
                    titleResId = R.string.settings_directory_appearance,
                    summary = UiText.Raw("calm_blue"),
                ),
                SettingsDirectoryRowUiModel(
                    id = "sound",
                    titleResId = R.string.settings_directory_sound,
                    summary = UiText.Raw("content://ringtone/demo"),
                ),
                SettingsDirectoryRowUiModel(
                    id = "calendar",
                    titleResId = R.string.settings_directory_calendar,
                    summary = UiText.Raw("Personal"),
                ),
                SettingsDirectoryRowUiModel(
                    id = "permissions",
                    titleResId = R.string.settings_directory_permissions,
                    summary = UiText.Raw("3"),
                ),
            ),
            permissionRows = listOf(
                PermissionGuidanceUiModel(
                    id = "notifications",
                    titleResId = R.string.settings_permission_notifications_title,
                    granted = false,
                    summaryResId = R.string.settings_permission_notifications_summary,
                    actionLabelResId = R.string.settings_permission_notifications_action,
                ),
            ),
        )
    }
}
