package com.cory.noter

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cory.noter.agent.AgentLoopRunner
import com.cory.noter.ai.AiAlarmCreator
import com.cory.noter.ai.AiAlarmPromptBuilder
import com.cory.noter.ai.AsrModel
import com.cory.noter.ai.OpenRouterModel
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.ui.NoterApp
import com.cory.noter.ui.Routes
import com.cory.noter.ui.ai.AiCreateScreen
import com.cory.noter.ui.ai.AiCreateViewModel
import com.cory.noter.ui.settings.AiVoiceSettingsScreen
import com.cory.noter.ui.settings.AppearanceSettingsScreen
import com.cory.noter.ui.settings.PermissionsSettingsScreen
import com.cory.noter.ui.settings.SettingsScreen
import com.cory.noter.ui.settings.SettingsTestTags
import com.cory.noter.ui.settings.SettingsViewModel
import com.cory.noter.ui.settings.SoundSettingsScreen
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class SettingsSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settings_route_navigates_to_appearance_detail() {
        setSettingsRouteTestContent()
        composeRule.onNodeWithTag(SettingsTestTags.AppearanceRow).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.AppearanceDetail).assertIsDisplayed()
    }

    @Test
    fun settings_route_navigates_to_ai_voice_detail() {
        setSettingsRouteTestContent()
        composeRule.onNodeWithTag(SettingsTestTags.AiVoiceRow).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.AiVoiceDetail).assertIsDisplayed()
    }

    @Test
    fun settings_route_navigates_to_sound_detail() {
        setSettingsRouteTestContent()
        composeRule.onNodeWithTag(SettingsTestTags.SoundRow).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SoundDetail).assertIsDisplayed()
    }

    @Test
    fun settings_route_navigates_to_permissions_detail() {
        setSettingsRouteTestContent()
        composeRule.onNodeWithTag(SettingsTestTags.PermissionsRow).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.PermissionsDetail).assertIsDisplayed()
    }

    @Test
    fun appearance_settings_detail_exposes_route_specific_controls() {
        val state = SettingsViewModelPreviewStates.default

        composeRule.setContent {
            MaterialTheme {
                AppearanceSettingsScreen(
                    state = state,
                    onThemePresetSelected = {},
                    onCustomThemeSeedColorChanged = {},
                    onSaveCustomThemeSeedColor = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.AppearanceDetail).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.ThemePresetAction("calm_blue")).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.ThemePresetAction("fresh_green")).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.ThemePresetAction("soft_rose")).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.ThemePresetAction("neutral_gray")).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.CustomThemeSeedInput).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.CustomThemeSeedSaveAction).assertIsDisplayed()
    }

    @Test
    fun ai_voice_settings_detail_exposes_route_specific_controls() {
        val state = SettingsViewModelPreviewStates.default

        composeRule.setContent {
            MaterialTheme {
                AiVoiceSettingsScreen(
                    state = state,
                    onApiKeyChanged = {},
                    onSaveApiKey = {},
                    onModelSelected = {},
                    onAsrModelSelected = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(SettingsTestTags.AiVoiceDetail).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.ApiKeyInput).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SaveApiKeyAction).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.ModelAction(OpenRouterModel.builtInIds[0])).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.AsrModelAction(AsrModel.builtInIds[0])).assertIsDisplayed()
    }

    @Test
    fun sound_settings_detail_exposes_route_specific_controls() {
        val state = SettingsViewModelPreviewStates.default

        composeRule.setContent {
            MaterialTheme {
                SoundSettingsScreen(
                    state = state,
                    onPickDefaultRingtone = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(SettingsTestTags.SoundDetail).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.DefaultRingtoneAction).assertIsDisplayed()
    }

    @Test
    fun permissions_settings_detail_exposes_route_specific_controls() {
        val state = SettingsViewModelPreviewStates.default

        composeRule.setContent {
            MaterialTheme {
                PermissionsSettingsScreen(
                    state = state,
                    onPermissionAction = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(SettingsTestTags.PermissionsDetail).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.PermissionAction("notifications")).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.PermissionAction("exact_alarms")).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.PermissionAction("battery_optimization")).assertIsDisplayed()
    }

    @Test
    fun settings_save_flow_persists_api_key_and_model_choice() {
        val repository = AndroidTestSettingsRepository()
        val viewModel = SettingsViewModel(
            settingsRepository = repository,
            exactAlarmPermissionReader = { true },
            notificationPermissionProvider = { true },
            batteryOptimizationIgnoredProvider = { false },
        )

        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MaterialTheme {
                AiVoiceSettingsScreen(
                    state = state,
                    onApiKeyChanged = viewModel::onApiKeyChanged,
                    onSaveApiKey = viewModel::saveApiKey,
                    onModelSelected = viewModel::onModelSelected,
                    onAsrModelSelected = viewModel::onAsrModelSelected,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.ApiKeyInput).performTextInput("sk-or-v1-demo")
        composeRule.onNodeWithTag(SettingsTestTags.SaveApiKeyAction).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.ModelAction(OpenRouterModel.builtInIds[0])).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.ModelAction(OpenRouterModel.builtInIds[1])).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.AsrModelAction(AsrModel.builtInIds[0])).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.AsrModelAction(AsrModel.builtInIds[1])).performClick()
        composeRule.waitForIdle()

        val settings = runBlocking { repository.settings.first() }
        assert(settings.openRouterApiKey == "sk-or-v1-demo")
        assert(settings.selectedModelId == OpenRouterModel.builtInIds[1])
        assert(settings.selectedAsrModelId == AsrModel.builtInIds[1])
    }

    @Test
    fun appearance_settings_save_flow_normalizes_hashless_custom_seed_color() {
        val repository = AndroidTestSettingsRepository()
        val viewModel = SettingsViewModel(
            settingsRepository = repository,
            exactAlarmPermissionReader = { true },
            notificationPermissionProvider = { true },
            batteryOptimizationIgnoredProvider = { false },
        )

        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MaterialTheme {
                AppearanceSettingsScreen(
                    state = state,
                    onThemePresetSelected = viewModel::onThemePresetSelected,
                    onCustomThemeSeedColorChanged = viewModel::onCustomThemeSeedColorChanged,
                    onSaveCustomThemeSeedColor = viewModel::saveCustomThemeSeedColor,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.CustomThemeSeedInput).performTextInput("4A6EA9")
        composeRule.onNodeWithTag(SettingsTestTags.CustomThemeSeedSaveAction).performClick()
        composeRule.waitForIdle()

        val settings = runBlocking { repository.settings.first() }
        assert(settings.themePresetId == com.cory.noter.domain.settings.AppSettings.CustomThemePresetId)
        assert(settings.customThemeSeedColor == "#4a6ea9")
    }

    @Test
    fun ai_create_missing_api_key_error_is_visible() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val clock = Clock.fixed(Instant.parse("2026-04-23T01:00:00Z"), zoneId)
        val settingsRepository = AndroidTestSettingsRepository()
        val viewModel = AiCreateViewModel(
            creator = AiAlarmCreator(
                settingsRepository = settingsRepository,
                agentLoopRunner = AgentLoopRunner(AndroidTestAgentLlmGateway()),
                alarmRepository = AndroidTestAlarmRepository(clock = clock, zoneId = zoneId),
                schedulingUseCase = AlarmSchedulingUseCase(AndroidTestAlarmScheduler()),
                promptBuilder = AiAlarmPromptBuilder(),
                clock = clock,
            ),
            settingsRepository = settingsRepository,
        )

        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MaterialTheme {
                AiCreateScreen(
                    state = state,
                    onPromptChanged = viewModel::onPromptChanged,
                    onSubmit = viewModel::submit,
                    onOpenExactAlarmSettings = {},
                    onOpenManualCreate = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Describe the alarm").performTextInput("wake me up tomorrow at 8")
        composeRule.onNodeWithText("Create with AI").performClick()

        composeRule.onNodeWithText(
            "Add an OpenRouter API key in Settings before using AI create.",
        ).assertIsDisplayed()
    }

    private fun setSettingsRouteTestContent() {
        composeRule.setContent {
            MaterialTheme {
                NoterApp(
                    unifiedAiCreateScreen = { _, _, _ -> Box(Modifier.testTag("ai-create")) },
                    alarmListScreen = { _, _, _, _ -> Box(Modifier.testTag("alarms")) },
                    alarmEditorScreen = { _, _ -> Box(Modifier.testTag("editor")) },
                    settingsScreen = { onOpenAppearance, onOpenAiVoice, onOpenSound, onOpenPermissions, _, _ ->
                        SettingsScreen(
                            state = SettingsViewModelPreviewStates.default,
                            onOpenAppearance = onOpenAppearance,
                            onOpenAiVoice = onOpenAiVoice,
                            onOpenSound = onOpenSound,
                            onOpenPermissions = onOpenPermissions,
                            onBack = {},
                        )
                    },
                    appearanceSettingsScreen = { _, _ ->
                        Box(Modifier.testTag(SettingsTestTags.AppearanceDetail))
                    },
                    aiVoiceSettingsScreen = { _, _ ->
                        Box(Modifier.testTag(SettingsTestTags.AiVoiceDetail))
                    },
                    soundSettingsScreen = { _, _ ->
                        Box(Modifier.testTag(SettingsTestTags.SoundDetail))
                    },
                    permissionsSettingsScreen = { _, _ ->
                        Box(Modifier.testTag(SettingsTestTags.PermissionsDetail))
                    },
                    startDestination = Routes.SETTINGS,
                )
            }
        }
    }
}

private object SettingsViewModelPreviewStates {
    val default = com.cory.noter.ui.settings.SettingsUiState(
        openRouterApiKey = "sk-demo",
        selectedModelId = OpenRouterModel.builtInIds[0],
        selectedAsrModelId = AsrModel.builtInIds[0],
        defaultRingtoneUri = "content://ringtone/demo",
        directoryRows = listOf(
            com.cory.noter.ui.settings.SettingsDirectoryRowUiModel(
                id = "appearance",
                titleResId = R.string.settings_directory_appearance,
                summary = com.cory.noter.ui.text.UiText.Raw("calm_blue"),
            ),
            com.cory.noter.ui.settings.SettingsDirectoryRowUiModel(
                id = "ai_voice",
                titleResId = R.string.settings_directory_ai_voice,
                summary = com.cory.noter.ui.text.UiText.Raw(OpenRouterModel.builtInIds[0]),
            ),
            com.cory.noter.ui.settings.SettingsDirectoryRowUiModel(
                id = "sound",
                titleResId = R.string.settings_directory_sound,
                summary = com.cory.noter.ui.text.UiText.Raw("content://ringtone/demo"),
            ),
            com.cory.noter.ui.settings.SettingsDirectoryRowUiModel(
                id = "permissions",
                titleResId = R.string.settings_directory_permissions,
                summary = com.cory.noter.ui.text.UiText.Raw("3"),
            ),
        ),
        permissionRows = listOf(
            com.cory.noter.ui.settings.PermissionGuidanceUiModel(
                id = "notifications",
                titleResId = R.string.settings_permission_notifications_title,
                granted = false,
                summaryResId = R.string.settings_permission_notifications_summary,
                actionLabelResId = R.string.settings_permission_notifications_action,
            ),
            com.cory.noter.ui.settings.PermissionGuidanceUiModel(
                id = "exact_alarms",
                titleResId = R.string.settings_permission_exact_alarms_title,
                granted = false,
                summaryResId = R.string.settings_permission_exact_alarms_summary,
                actionLabelResId = R.string.settings_permission_exact_alarms_action,
            ),
            com.cory.noter.ui.settings.PermissionGuidanceUiModel(
                id = "battery_optimization",
                titleResId = R.string.settings_permission_battery_title,
                granted = false,
                summaryResId = R.string.settings_permission_battery_summary,
                actionLabelResId = R.string.settings_permission_battery_action,
            ),
        ),
    )
}
