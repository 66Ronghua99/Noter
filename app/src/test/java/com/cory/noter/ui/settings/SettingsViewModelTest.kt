package com.cory.noter.ui.settings

import com.cory.noter.ai.OpenRouterModel
import com.cory.noter.data.settings.FakeSettingsRepository
import com.cory.noter.permissions.PermissionStatusReader
import com.cory.noter.ui.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `selecting model saves settings value`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(
            settingsRepository = repository,
            exactAlarmPermissionReader = PermissionStatusReader { true },
            notificationPermissionGranted = false,
            batteryOptimizationIgnored = false,
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
    fun `selecting ringtone saves settings value`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(
            settingsRepository = repository,
            exactAlarmPermissionReader = PermissionStatusReader { false },
            notificationPermissionGranted = true,
            batteryOptimizationIgnored = true,
        )

        advanceUntilIdle()
        viewModel.onDefaultRingtoneSelected("content://media/internal/audio/media/12")
        advanceUntilIdle()

        assertThat(repository.settings.first().defaultRingtoneUri)
            .isEqualTo("content://media/internal/audio/media/12")
        assertThat(viewModel.uiState.value.defaultRingtoneUri)
            .isEqualTo("content://media/internal/audio/media/12")
    }
}
