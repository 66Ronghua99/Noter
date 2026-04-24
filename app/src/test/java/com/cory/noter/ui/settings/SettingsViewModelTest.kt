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
}
