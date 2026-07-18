package com.cory.noter.ui.settings

import com.cory.noter.calendar.CalendarSource
import com.cory.noter.calendar.CalendarSourceResult
import com.cory.noter.calendar.DeviceCalendar
import com.cory.noter.data.settings.FakeSettingsRepository
import com.cory.noter.domain.settings.AppSettings
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
    fun `directory contains only retained settings destinations`() = runTest {
        val viewModel = viewModel(
            repository = FakeSettingsRepository(
                AppSettings(
                    defaultRingtoneUri = "content://ringtone/demo",
                    themePresetId = "fresh_green",
                ),
            ),
        )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.directoryRows.map { it.id })
            .containsExactly("appearance", "sound", "calendar", "permissions")
            .inOrder()
    }

    @Test
    fun `theme state and writes stay repository owned`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(repository)

        advanceUntilIdle()
        viewModel.onThemePresetSelected("soft_rose")
        advanceUntilIdle()

        assertThat(repository.settings.first().themePresetId).isEqualTo("soft_rose")
        assertThat(viewModel.uiState.value.themePresetId).isEqualTo("soft_rose")

        viewModel.onCustomThemeSeedColorChanged(" #B65B70 ")
        viewModel.saveCustomThemeSeedColor()
        advanceUntilIdle()

        assertThat(repository.settings.first().themePresetId).isEqualTo(AppSettings.CustomThemePresetId)
        assertThat(repository.settings.first().customThemeSeedColor).isEqualTo("#b65b70")
    }

    @Test
    fun `missing calendar permission is explicit`() = runTest {
        val viewModel = viewModel(
            calendarSource = FakeCalendarSource(CalendarSourceResult.MissingPermission),
        )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.calendarSettings.status)
            .isEqualTo(CalendarSettingsStatus.MISSING_PERMISSION)
        assertThat(viewModel.uiState.value.calendarSettings.setupComplete).isFalse()
    }

    @Test
    fun `read only calendars are not considered ready`() = runTest {
        val viewModel = viewModel(
            calendarSource = FakeCalendarSource.available(
                DeviceCalendar(
                    id = 7L,
                    displayName = "Read only",
                    accountName = "readonly@example.com",
                    accountType = "com.google",
                    writable = false,
                ),
            ),
        )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.calendarSettings.status)
            .isEqualTo(CalendarSettingsStatus.NO_WRITABLE_CALENDAR)
    }

    @Test
    fun `selected writable calendar is ready and can be cleared`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(
            repository = repository,
            calendarSource = FakeCalendarSource.available(writableCalendar()),
        )

        advanceUntilIdle()
        viewModel.onDefaultCalendarSelected(42L)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.calendarSettings.status)
            .isEqualTo(CalendarSettingsStatus.READY)
        assertThat(repository.settings.first().defaultCalendarId).isEqualTo(42L)

        viewModel.clearDefaultCalendar()
        advanceUntilIdle()

        assertThat(repository.settings.first().defaultCalendarId).isNull()
    }

    @Test
    fun `invalid stored calendar is reported`() = runTest {
        val viewModel = viewModel(
            repository = FakeSettingsRepository(
                AppSettings(
                    defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
                    defaultCalendarId = 99L,
                ),
            ),
            calendarSource = FakeCalendarSource.available(writableCalendar()),
        )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.calendarSettings.status)
            .isEqualTo(CalendarSettingsStatus.INVALID_STORED_CALENDAR)
    }

    @Test
    fun `permission rows refresh from current device state`() = runTest {
        var notificationsGranted = false
        var exactAlarmsGranted = false
        var batteryIgnored = false
        val viewModel = viewModel(
            exactAlarmPermissionReader = PermissionStatusReader { exactAlarmsGranted },
            notificationPermissionProvider = { notificationsGranted },
            batteryOptimizationIgnoredProvider = { batteryIgnored },
        )

        advanceUntilIdle()
        assertThat(viewModel.uiState.value.permissionRows.any { !it.granted }).isTrue()

        notificationsGranted = true
        exactAlarmsGranted = true
        batteryIgnored = true
        viewModel.refreshPermissionRows()

        assertThat(viewModel.uiState.value.permissionRows.all { it.granted }).isTrue()
    }

    private fun viewModel(
        repository: FakeSettingsRepository = FakeSettingsRepository(),
        exactAlarmPermissionReader: PermissionStatusReader = PermissionStatusReader { true },
        notificationPermissionProvider: () -> Boolean = { true },
        batteryOptimizationIgnoredProvider: () -> Boolean = { true },
        calendarSource: CalendarSource = FakeCalendarSource.available(),
    ): SettingsViewModel = SettingsViewModel(
        settingsRepository = repository,
        exactAlarmPermissionReader = exactAlarmPermissionReader,
        notificationPermissionProvider = notificationPermissionProvider,
        batteryOptimizationIgnoredProvider = batteryOptimizationIgnoredProvider,
        calendarSource = calendarSource,
    )

    private fun writableCalendar(): DeviceCalendar = DeviceCalendar(
        id = 42L,
        displayName = "Personal",
        accountName = "me@example.com",
        accountType = "com.google",
        writable = true,
    )

    private class FakeCalendarSource(
        private val result: CalendarSourceResult,
    ) : CalendarSource {
        override suspend fun loadCalendars(): CalendarSourceResult = result

        companion object {
            fun available(vararg calendars: DeviceCalendar): FakeCalendarSource =
                FakeCalendarSource(CalendarSourceResult.Available(calendars.toList()))
        }
    }
}
