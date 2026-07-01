package com.cory.noter.ui.settings

import android.content.Context
import com.cory.noter.ai.AsrModel
import com.cory.noter.ai.OpenRouterModel
import com.cory.noter.data.settings.FakeSettingsRepository
import com.cory.noter.domain.settings.AppSettings
import com.cory.noter.permissions.PermissionStatusReader
import com.cory.noter.ui.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.junit.Rule
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `directory summaries expose appearance ai sound and permissions state`() = runTest {
        val repository = FakeSettingsRepository(
            initialSettings = AppSettings(
                openRouterApiKey = "sk-demo",
                selectedModelId = OpenRouterModel.builtInIds[1],
                selectedAsrModelId = AsrModel.builtInIds[1],
                defaultRingtoneUri = "content://ringtone/demo",
                themePresetId = "fresh_green",
            ),
        )
        val viewModel = SettingsViewModel(
            settingsRepository = repository,
            exactAlarmPermissionReader = PermissionStatusReader { false },
            notificationPermissionProvider = { true },
            batteryOptimizationIgnoredProvider = { false },
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.directoryRows.map { it.id })
            .containsExactly("appearance", "ai_voice", "sound", "permissions")
            .inOrder()
        assertThat(state.directoryRows.first { it.id == "appearance" }.summary.asStringForTest())
            .contains("Fresh Green")
        assertThat(state.directoryRows.first { it.id == "ai_voice" }.summary.asStringForTest())
            .contains(OpenRouterModel.builtInIds[1])
        assertThat(state.directoryRows.first { it.id == "sound" }.summary.asStringForTest())
            .contains("content://ringtone/demo")
        assertThat(state.directoryRows.first { it.id == "permissions" }.summary.asStringForTest())
            .contains("2")
    }

    @Test
    fun `theme state follows repository settings`() = runTest {
        val repository = FakeSettingsRepository(
            initialSettings = AppSettings(
                openRouterApiKey = "",
                selectedModelId = OpenRouterModel.DefaultId,
                selectedAsrModelId = AsrModel.DefaultId,
                defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
                themePresetId = AppSettings.CustomThemePresetId,
                customThemeSeedColor = "#b65b70",
            ),
        )
        val viewModel = SettingsViewModel(
            settingsRepository = repository,
            exactAlarmPermissionReader = PermissionStatusReader { true },
            notificationPermissionProvider = { true },
            batteryOptimizationIgnoredProvider = { true },
        )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.themePresetId).isEqualTo(AppSettings.CustomThemePresetId)
        assertThat(viewModel.uiState.value.customThemeSeedColor).isEqualTo("#b65b70")
        assertThat(viewModel.uiState.value.customThemeSeedColorInput).isEqualTo("#b65b70")
    }

    @Test
    fun `selecting theme preset saves repository value`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(
            settingsRepository = repository,
            exactAlarmPermissionReader = PermissionStatusReader { true },
            notificationPermissionProvider = { true },
            batteryOptimizationIgnoredProvider = { true },
        )

        advanceUntilIdle()
        viewModel.onThemePresetSelected("soft_rose")
        advanceUntilIdle()

        assertThat(repository.settings.first().themePresetId).isEqualTo("soft_rose")
        assertThat(repository.settings.first().customThemeSeedColor).isNull()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `saving custom theme seed color saves repository value`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(
            settingsRepository = repository,
            exactAlarmPermissionReader = PermissionStatusReader { true },
            notificationPermissionProvider = { true },
            batteryOptimizationIgnoredProvider = { true },
        )

        advanceUntilIdle()
        viewModel.onCustomThemeSeedColorChanged("#4A6EA9")
        viewModel.saveCustomThemeSeedColor()
        advanceUntilIdle()

        assertThat(repository.settings.first().themePresetId).isEqualTo(AppSettings.CustomThemePresetId)
        assertThat(repository.settings.first().customThemeSeedColor).isEqualTo("#4a6ea9")
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `invalid appearance writes surface explicit errors`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(
            settingsRepository = repository,
            exactAlarmPermissionReader = PermissionStatusReader { true },
            notificationPermissionProvider = { true },
            batteryOptimizationIgnoredProvider = { true },
        )

        advanceUntilIdle()
        viewModel.onThemePresetSelected("electric_ultraviolet")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.errorMessage?.asStringForTest())
            .contains("UNKNOWN_THEME_PRESET_ID")

        viewModel.onCustomThemeSeedColorChanged("not-a-color")
        viewModel.saveCustomThemeSeedColor()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage?.asStringForTest())
            .contains("INVALID_THEME_SEED_COLOR")
    }

    @Test
    fun `selecting model saves settings value`() = runTest {
        val repository = FakeSettingsRepository()
        var notificationGranted = false
        val viewModel = SettingsViewModel(
            settingsRepository = repository,
            exactAlarmPermissionReader = PermissionStatusReader { true },
            notificationPermissionProvider = { notificationGranted },
            batteryOptimizationIgnoredProvider = { false },
        )

        advanceUntilIdle()
        viewModel.onModelSelected(OpenRouterModel.builtInIds[1])
        advanceUntilIdle()

        assertThat(repository.settings.first().selectedModelId)
            .isEqualTo(OpenRouterModel.builtInIds[1])
        assertThat(viewModel.uiState.value.selectedModelId)
            .isEqualTo(OpenRouterModel.builtInIds[1])
    }

    @Test
    fun `asr model options are exposed independently from llm model options`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(
            settingsRepository = repository,
            exactAlarmPermissionReader = PermissionStatusReader { true },
            notificationPermissionProvider = { true },
            batteryOptimizationIgnoredProvider = { true },
        )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.modelOptions).isEqualTo(OpenRouterModel.builtInIds)
        assertThat(viewModel.uiState.value.asrModelOptions).isEqualTo(AsrModel.builtInIds)
        assertThat(viewModel.uiState.value.selectedAsrModelId).isEqualTo(AsrModel.DefaultId)
    }

    @Test
    fun `selecting asr model saves settings value`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(
            settingsRepository = repository,
            exactAlarmPermissionReader = PermissionStatusReader { true },
            notificationPermissionProvider = { true },
            batteryOptimizationIgnoredProvider = { false },
        )

        advanceUntilIdle()
        viewModel.onAsrModelSelected(AsrModel.builtInIds[1])
        advanceUntilIdle()

        assertThat(repository.settings.first().selectedAsrModelId)
            .isEqualTo(AsrModel.builtInIds[1])
        assertThat(viewModel.uiState.value.selectedAsrModelId)
            .isEqualTo(AsrModel.builtInIds[1])
    }

    @Test
    fun `unknown asr model selection surfaces error and preserves current value`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(
            settingsRepository = repository,
            exactAlarmPermissionReader = PermissionStatusReader { true },
            notificationPermissionProvider = { true },
            batteryOptimizationIgnoredProvider = { true },
        )

        advanceUntilIdle()
        viewModel.onAsrModelSelected("unknown/asr-model")
        advanceUntilIdle()

        assertThat(repository.settings.first().selectedAsrModelId).isEqualTo(AsrModel.DefaultId)
        assertThat(viewModel.uiState.value.selectedAsrModelId).isEqualTo(AsrModel.DefaultId)
        assertThat(viewModel.uiState.value.errorMessage?.asStringForTest())
            .contains("UNKNOWN_ASR_MODEL_ID")
    }

    @Test
    fun `selecting ringtone saves settings value`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(
            settingsRepository = repository,
            exactAlarmPermissionReader = PermissionStatusReader { false },
            notificationPermissionProvider = { true },
            batteryOptimizationIgnoredProvider = { true },
        )

        advanceUntilIdle()
        viewModel.onDefaultRingtoneSelected("content://media/internal/audio/media/12")
        advanceUntilIdle()

        assertThat(repository.settings.first().defaultRingtoneUri)
            .isEqualTo("content://media/internal/audio/media/12")
        assertThat(viewModel.uiState.value.defaultRingtoneUri)
            .isEqualTo("content://media/internal/audio/media/12")
    }

    @Test
    fun `refresh permission rows re-reads current permission state`() = runTest {
        val repository = FakeSettingsRepository()
        var notificationGranted = false
        var batteryIgnored = false
        var exactAllowed = false
        val viewModel = SettingsViewModel(
            settingsRepository = repository,
            exactAlarmPermissionReader = PermissionStatusReader { exactAllowed },
            notificationPermissionProvider = { notificationGranted },
            batteryOptimizationIgnoredProvider = { batteryIgnored },
        )

        advanceUntilIdle()
        assertThat(viewModel.uiState.value.permissionRows.first { it.id == "notifications" }.granted)
            .isFalse()

        notificationGranted = true
        batteryIgnored = true
        exactAllowed = true
        viewModel.refreshPermissionRows()

        assertThat(viewModel.uiState.value.permissionRows.first { it.id == "notifications" }.granted)
            .isTrue()
        assertThat(viewModel.uiState.value.permissionRows.first { it.id == "exact_alarms" }.granted)
            .isTrue()
        assertThat(viewModel.uiState.value.permissionRows.first { it.id == "battery_optimization" }.granted)
            .isTrue()
    }

    private fun com.cory.noter.ui.text.UiText.asStringForTest(): String = when (this) {
        is com.cory.noter.ui.text.UiText.Raw -> value
        is com.cory.noter.ui.text.UiText.Resource -> {
            RuntimeEnvironment.getApplication()
                .getString(resId, *args.toTypedArray())
        }
    }
}
