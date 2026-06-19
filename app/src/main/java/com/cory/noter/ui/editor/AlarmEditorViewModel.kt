package com.cory.noter.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cory.noter.R
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.ScheduleResult
import com.cory.noter.data.alarm.AlarmDraft
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.data.settings.SettingsRepository
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.AlarmValidation
import com.cory.noter.domain.alarm.RepeatRule
import com.cory.noter.ui.text.UiText
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EditorRepeatOption {
    ONCE,
    DAILY,
    WEEKDAYS,
    CUSTOM,
    INTERVAL,
}

data class AlarmEditorUiState(
    val title: String = "",
    val hourText: String = "8",
    val minuteText: String = "00",
    val repeatOption: EditorRepeatOption = EditorRepeatOption.ONCE,
    val onceDateText: String = "",
    val intervalStartDateText: String = "",
    val intervalEndDateText: String = "",
    val intervalWeeksText: String = "1",
    val customWeekdays: Set<DayOfWeek> = emptySet(),
    val ringtoneUri: String = "",
    val enabled: Boolean = true,
    val isExisting: Boolean = false,
    val validationErrors: List<AlarmValidation.Error> = emptyList(),
    val errorMessage: UiText? = null,
    val exactAlarmPermissionRequired: Boolean = false,
    val savedAlarmId: Long? = null,
    val deleted: Boolean = false,
)

class AlarmEditorViewModel(
    private val alarmId: Long?,
    private val repository: AlarmRepository,
    private val settingsRepository: SettingsRepository,
    private val schedulingUseCase: AlarmSchedulingUseCase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val initialDate: LocalDate = LocalDate.now(clock.withZone(zoneId))

    private val mutableUiState = MutableStateFlow(
        AlarmEditorUiState(
            onceDateText = initialDate.plusDays(1).toString(),
            intervalStartDateText = initialDate.toString(),
            intervalEndDateText = initialDate.plusYears(1).toString(),
        ),
    )
    val uiState: StateFlow<AlarmEditorUiState> = mutableUiState.asStateFlow()

    private var existingAlarm: Alarm? = null

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val loadedAlarm = alarmId?.let { repository.get(it) }
            existingAlarm = loadedAlarm
            mutableUiState.update { current ->
                if (loadedAlarm != null) {
                    current.copy(
                        title = loadedAlarm.title,
                        hourText = loadedAlarm.hour.toString(),
                        minuteText = loadedAlarm.minute.toString().padStart(2, '0'),
                        repeatOption = loadedAlarm.repeatRule.toEditorOption(),
                        onceDateText = (loadedAlarm.repeatRule as? RepeatRule.Once)?.date?.toString()
                            ?: current.onceDateText,
                        intervalStartDateText = (loadedAlarm.repeatRule as? RepeatRule.WeeklyInterval)?.startDate
                            ?.toString()
                            ?: current.intervalStartDateText,
                        intervalEndDateText = (loadedAlarm.repeatRule as? RepeatRule.WeeklyInterval)?.endDate
                            ?.toString()
                            ?: current.intervalEndDateText,
                        intervalWeeksText = (loadedAlarm.repeatRule as? RepeatRule.WeeklyInterval)?.intervalWeeks
                            ?.toString()
                            ?: current.intervalWeeksText,
                        customWeekdays = when (val repeatRule = loadedAlarm.repeatRule) {
                            is RepeatRule.CustomWeekdays -> repeatRule.days
                            is RepeatRule.WeeklyInterval -> repeatRule.days
                            else -> emptySet()
                        },
                        ringtoneUri = loadedAlarm.ringtoneUri,
                        enabled = loadedAlarm.enabled,
                        isExisting = true,
                    )
                } else {
                    current.copy(ringtoneUri = settings.defaultRingtoneUri)
                }
            }
        }
    }

    fun onTitleChanged(title: String) {
        mutableUiState.update {
            it.copy(
                title = title,
                validationErrors = emptyList(),
                errorMessage = null,
                exactAlarmPermissionRequired = false,
            )
        }
    }

    fun onHourChanged(hourText: String) {
        mutableUiState.update {
            it.copy(
                hourText = hourText,
                validationErrors = emptyList(),
                errorMessage = null,
                exactAlarmPermissionRequired = false,
            )
        }
    }

    fun onHourSelected(hour: Int) {
        require(hour in 0..23) { "Hour must be between 0 and 23." }
        onHourChanged(hour.toString())
    }

    fun onMinuteChanged(minuteText: String) {
        mutableUiState.update {
            it.copy(
                minuteText = minuteText,
                validationErrors = emptyList(),
                errorMessage = null,
                exactAlarmPermissionRequired = false,
            )
        }
    }

    fun onMinuteSelected(minute: Int) {
        require(minute in 0..59) { "Minute must be between 0 and 59." }
        onMinuteChanged(minute.toString().padStart(2, '0'))
    }

    fun onRepeatRuleChanged(repeatOption: EditorRepeatOption) {
        mutableUiState.update {
            it.copy(
                repeatOption = repeatOption,
                validationErrors = emptyList(),
                errorMessage = null,
                exactAlarmPermissionRequired = false,
            )
        }
    }

    fun onOnceDateChanged(onceDateText: String) {
        mutableUiState.update {
            it.copy(
                onceDateText = onceDateText,
                validationErrors = emptyList(),
                errorMessage = null,
                exactAlarmPermissionRequired = false,
            )
        }
    }

    fun onOnceDateSelected(date: LocalDate) {
        onOnceDateChanged(date.toString())
    }

    fun onCustomWeekdayToggled(day: DayOfWeek) {
        mutableUiState.update { current ->
            val nextDays = current.customWeekdays.toMutableSet().apply {
                if (!add(day)) {
                    remove(day)
                }
            }
            current.copy(
                customWeekdays = nextDays,
                validationErrors = emptyList(),
                errorMessage = null,
                exactAlarmPermissionRequired = false,
            )
        }
    }

    fun onIntervalStartDateChanged(intervalStartDateText: String) {
        mutableUiState.update { current ->
            val selectedStartDate = runCatching { LocalDate.parse(intervalStartDateText) }.getOrNull()
            val currentEndDate = runCatching { LocalDate.parse(current.intervalEndDateText) }.getOrNull()
            val nextEndDateText = if (
                selectedStartDate != null &&
                currentEndDate != null &&
                selectedStartDate.isAfter(currentEndDate)
            ) {
                intervalStartDateText
            } else {
                current.intervalEndDateText
            }

            current.copy(
                intervalStartDateText = intervalStartDateText,
                intervalEndDateText = nextEndDateText,
                validationErrors = emptyList(),
                errorMessage = null,
                exactAlarmPermissionRequired = false,
            )
        }
    }

    fun onIntervalStartDateSelected(date: LocalDate) {
        onIntervalStartDateChanged(date.toString())
    }

    fun onIntervalEndDateChanged(intervalEndDateText: String) {
        mutableUiState.update {
            it.copy(
                intervalEndDateText = intervalEndDateText,
                validationErrors = emptyList(),
                errorMessage = null,
                exactAlarmPermissionRequired = false,
            )
        }
    }

    fun onIntervalEndDateSelected(date: LocalDate) {
        onIntervalEndDateChanged(date.toString())
    }

    fun onIntervalWeeksChanged(intervalWeeksText: String) {
        mutableUiState.update {
            it.copy(
                intervalWeeksText = intervalWeeksText,
                validationErrors = emptyList(),
                errorMessage = null,
                exactAlarmPermissionRequired = false,
            )
        }
    }

    fun onIntervalWeeksSelected(intervalWeeks: Int) {
        onIntervalWeeksChanged(intervalWeeks.coerceIn(1, 104).toString())
    }

    fun onRingtoneSelected(ringtoneUri: String) {
        mutableUiState.update {
            it.copy(
                ringtoneUri = ringtoneUri,
                errorMessage = null,
                exactAlarmPermissionRequired = false,
            )
        }
    }

    fun onEnabledChanged(enabled: Boolean) {
        mutableUiState.update {
            it.copy(
                enabled = enabled,
                errorMessage = null,
                exactAlarmPermissionRequired = false,
            )
        }
    }

    fun save() {
        viewModelScope.launch {
            val state = uiState.value
            val hour = state.hourText.toIntOrNull() ?: -1
            val minute = state.minuteText.toIntOrNull() ?: -1
            val repeatRule = buildRepeatRule(state) ?: return@launch
            val validationErrors = AlarmValidation.validateDraft(
                title = state.title,
                hour = hour,
                minute = minute,
                repeatRule = repeatRule,
                now = clock.instant(),
                zoneId = zoneId,
            )
            if (validationErrors.isNotEmpty()) {
                mutableUiState.update {
                    it.copy(validationErrors = validationErrors)
                }
                return@launch
            }

            val savedAlarm = runCatching {
                val existing = existingAlarm
                if (existing == null) {
                    repository.create(
                        AlarmDraft(
                            title = state.title.trim(),
                            hour = hour,
                            minute = minute,
                            repeatRule = repeatRule,
                            enabled = state.enabled,
                            ringtoneUri = state.ringtoneUri,
                            source = AlarmSource.MANUAL,
                            aiOriginalText = null,
                        ),
                    )
                } else {
                    repository.update(
                        existing.copy(
                            title = state.title.trim(),
                            hour = hour,
                            minute = minute,
                            repeatRule = repeatRule,
                            enabled = state.enabled,
                            ringtoneUri = state.ringtoneUri,
                        ),
                    )
                }
            }.getOrElse { error ->
                mutableUiState.update {
                    it.copy(
                        errorMessage = error.message
                            ?.let(UiText::Raw)
                            ?: UiText.Resource(R.string.editor_save_failed_default),
                    )
                }
                return@launch
            }

            existingAlarm = savedAlarm
            val scheduleResult = schedulingUseCase.syncSchedule(savedAlarm)
            val errorMessage = scheduleResult.toEditorMessage()
            mutableUiState.update {
                it.copy(
                    savedAlarmId = savedAlarm.id.takeIf { errorMessage == null },
                    validationErrors = emptyList(),
                    errorMessage = errorMessage,
                    exactAlarmPermissionRequired = scheduleResult is ScheduleResult.MissingPermission,
                    isExisting = true,
                )
            }
        }
    }

    fun delete() {
        val existing = existingAlarm ?: return
        viewModelScope.launch {
            repository.delete(existing.id)
            val scheduleResult = schedulingUseCase.cancel(existing.id)
            mutableUiState.update {
                it.copy(
                    deleted = true,
                    errorMessage = scheduleResult.toEditorMessage(),
                )
            }
        }
    }

    private fun buildRepeatRule(state: AlarmEditorUiState): RepeatRule? = when (state.repeatOption) {
        EditorRepeatOption.ONCE -> {
            val onceDate = runCatching { LocalDate.parse(state.onceDateText) }.getOrNull()
            if (onceDate == null) {
                mutableUiState.update {
                    it.copy(errorMessage = UiText.Resource(R.string.editor_invalid_once_date))
                }
                null
            } else {
                RepeatRule.Once(onceDate)
            }
        }

        EditorRepeatOption.DAILY -> RepeatRule.Daily
        EditorRepeatOption.WEEKDAYS -> RepeatRule.Weekdays
        EditorRepeatOption.CUSTOM -> RepeatRule.CustomWeekdays(state.customWeekdays)
        EditorRepeatOption.INTERVAL -> buildIntervalRepeatRule(state)
    }

    private fun buildIntervalRepeatRule(state: AlarmEditorUiState): RepeatRule? {
        val startDate = runCatching { LocalDate.parse(state.intervalStartDateText) }.getOrNull()
        if (startDate == null) {
            mutableUiState.update {
                it.copy(errorMessage = UiText.Resource(R.string.editor_invalid_interval_start_date))
            }
            return null
        }

        val endDate = runCatching { LocalDate.parse(state.intervalEndDateText) }.getOrNull()
        if (endDate == null) {
            mutableUiState.update {
                it.copy(errorMessage = UiText.Resource(R.string.editor_invalid_interval_end_date))
            }
            return null
        }

        val intervalWeeks = state.intervalWeeksText.toIntOrNull()
        if (intervalWeeks == null || intervalWeeks <= 0) {
            mutableUiState.update {
                it.copy(errorMessage = UiText.Resource(R.string.editor_invalid_interval_weeks_input))
            }
            return null
        }

        if (endDate.isBefore(startDate)) {
            mutableUiState.update {
                it.copy(errorMessage = UiText.Resource(R.string.editor_validation_invalid_interval_range))
            }
            return null
        }

        return RepeatRule.WeeklyInterval(
            startDate = startDate,
            endDate = endDate,
            intervalWeeks = intervalWeeks,
            days = state.customWeekdays,
        )
    }

    private fun RepeatRule.toEditorOption(): EditorRepeatOption = when (this) {
        is RepeatRule.Once -> EditorRepeatOption.ONCE
        RepeatRule.Daily -> EditorRepeatOption.DAILY
        RepeatRule.Weekdays -> EditorRepeatOption.WEEKDAYS
        is RepeatRule.CustomWeekdays -> EditorRepeatOption.CUSTOM
        is RepeatRule.WeeklyInterval -> EditorRepeatOption.INTERVAL
    }

    private fun ScheduleResult.toEditorMessage(): UiText? = when (this) {
        ScheduleResult.Scheduled,
        ScheduleResult.Cancelled,
        -> null

        is ScheduleResult.MissingPermission -> UiText.Resource(
            R.string.editor_schedule_missing_permission,
            listOf(permission),
        )
        is ScheduleResult.Failed -> UiText.Raw(reason)
    }
}
