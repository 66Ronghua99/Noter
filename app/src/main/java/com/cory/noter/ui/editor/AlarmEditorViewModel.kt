package com.cory.noter.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.ScheduleResult
import com.cory.noter.data.alarm.AlarmDraft
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.data.settings.SettingsRepository
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmSource
import com.cory.noter.domain.alarm.AlarmValidation
import com.cory.noter.domain.alarm.RepeatRule
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
}

data class AlarmEditorUiState(
    val title: String = "",
    val hourText: String = "8",
    val minuteText: String = "00",
    val repeatOption: EditorRepeatOption = EditorRepeatOption.ONCE,
    val onceDateText: String = "",
    val customWeekdays: Set<DayOfWeek> = emptySet(),
    val ringtoneUri: String = "",
    val enabled: Boolean = true,
    val isExisting: Boolean = false,
    val validationErrors: List<AlarmValidation.Error> = emptyList(),
    val errorMessage: String? = null,
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
    private val mutableUiState = MutableStateFlow(
        AlarmEditorUiState(
            onceDateText = LocalDate.now(clock.withZone(zoneId)).plusDays(1).toString(),
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
                        customWeekdays = (loadedAlarm.repeatRule as? RepeatRule.CustomWeekdays)?.days
                            ?: emptySet(),
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
            )
        }
    }

    fun onHourChanged(hourText: String) {
        mutableUiState.update {
            it.copy(
                hourText = hourText,
                validationErrors = emptyList(),
                errorMessage = null,
            )
        }
    }

    fun onMinuteChanged(minuteText: String) {
        mutableUiState.update {
            it.copy(
                minuteText = minuteText,
                validationErrors = emptyList(),
                errorMessage = null,
            )
        }
    }

    fun onRepeatRuleChanged(repeatOption: EditorRepeatOption) {
        mutableUiState.update {
            it.copy(
                repeatOption = repeatOption,
                validationErrors = emptyList(),
                errorMessage = null,
            )
        }
    }

    fun onOnceDateChanged(onceDateText: String) {
        mutableUiState.update {
            it.copy(
                onceDateText = onceDateText,
                validationErrors = emptyList(),
                errorMessage = null,
            )
        }
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
            )
        }
    }

    fun onRingtoneSelected(ringtoneUri: String) {
        mutableUiState.update {
            it.copy(
                ringtoneUri = ringtoneUri,
                errorMessage = null,
            )
        }
    }

    fun onEnabledChanged(enabled: Boolean) {
        mutableUiState.update {
            it.copy(
                enabled = enabled,
                errorMessage = null,
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
                    it.copy(errorMessage = error.message ?: "Unable to save alarm.")
                }
                return@launch
            }

            existingAlarm = savedAlarm
            val scheduleResult = schedulingUseCase.syncSchedule(savedAlarm)
            mutableUiState.update {
                it.copy(
                    savedAlarmId = savedAlarm.id,
                    validationErrors = emptyList(),
                    errorMessage = scheduleResult.toEditorMessage(),
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
                    it.copy(errorMessage = "Enter a valid date in YYYY-MM-DD format.")
                }
                null
            } else {
                RepeatRule.Once(onceDate)
            }
        }

        EditorRepeatOption.DAILY -> RepeatRule.Daily
        EditorRepeatOption.WEEKDAYS -> RepeatRule.Weekdays
        EditorRepeatOption.CUSTOM -> RepeatRule.CustomWeekdays(state.customWeekdays)
    }

    private fun RepeatRule.toEditorOption(): EditorRepeatOption = when (this) {
        is RepeatRule.Once -> EditorRepeatOption.ONCE
        RepeatRule.Daily -> EditorRepeatOption.DAILY
        RepeatRule.Weekdays -> EditorRepeatOption.WEEKDAYS
        is RepeatRule.CustomWeekdays -> EditorRepeatOption.CUSTOM
    }

    private fun ScheduleResult.toEditorMessage(): String? = when (this) {
        ScheduleResult.Scheduled,
        ScheduleResult.Cancelled,
        -> null

        is ScheduleResult.MissingPermission -> "Alarm saved but scheduling needs permission: $permission"
        is ScheduleResult.Failed -> reason
    }
}
