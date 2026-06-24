package com.cory.noter

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cory.noter.agent.AgentLoopRunner
import com.cory.noter.ai.AiAlarmCreator
import com.cory.noter.ai.AiAlarmPromptBuilder
import com.cory.noter.ai.AsrModel
import com.cory.noter.ai.OpenRouterModel
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.ui.ai.AiCreateScreen
import com.cory.noter.ui.ai.AiCreateViewModel
import com.cory.noter.ui.settings.SettingsScreen
import com.cory.noter.ui.settings.SettingsViewModel
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
                SettingsScreen(
                    state = state,
                    onApiKeyChanged = viewModel::onApiKeyChanged,
                    onSaveApiKey = viewModel::saveApiKey,
                    onModelSelected = viewModel::onModelSelected,
                    onAsrModelSelected = viewModel::onAsrModelSelected,
                    onPickDefaultRingtone = {},
                    onPermissionAction = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("API key").performTextInput("sk-or-v1-demo")
        composeRule.onNodeWithText("Save API key").performClick()
        composeRule.onNodeWithText(OpenRouterModel.builtInIds[1]).performClick()
        composeRule.onNodeWithText(AsrModel.builtInIds[1]).performClick()
        composeRule.waitForIdle()

        val settings = runBlocking { repository.settings.first() }
        assert(settings.openRouterApiKey == "sk-or-v1-demo")
        assert(settings.selectedModelId == OpenRouterModel.builtInIds[1])
        assert(settings.selectedAsrModelId == AsrModel.builtInIds[1])
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
}
