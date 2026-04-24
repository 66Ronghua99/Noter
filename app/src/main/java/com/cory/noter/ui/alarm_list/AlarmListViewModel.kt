package com.cory.noter.ui.alarm_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.ScheduleResult
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.RepeatRule
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlarmListUiState(
    val alarms: List<AlarmListItemUiModel> = emptyList(),
    val errorMessage: String? = null,
)

data class AlarmListItemUiModel(
    val id: Long,
    val title: String,
    val nextTriggerText: String,
    val repeatLabel: String,
    val enabled: Boolean,
)

class AlarmListViewModel(
    private val repository: AlarmRepository,
    private val schedulingUseCase: AlarmSchedulingUseCase,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AlarmListUiState())
    val uiState: StateFlow<AlarmListUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.alarms.collect { alarms ->
                mutableUiState.update { current ->
                    current.copy(
                        alarms = alarms.map(::toUiModel),
                    )
                }
            }
        }
    }

    fun onAlarmEnabledChanged(
        alarmId: Long,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            val updatedAlarm = if (enabled) {
                repository.enable(alarmId)
            } else {
                repository.disable(alarmId)
            } ?: return@launch

            handleScheduleResult(
                result = schedulingUseCase.syncSchedule(updatedAlarm),
                missingPermissionPrefix = "Alarm saved but scheduling needs permission",
            )
        }
    }

    fun onDeleteAlarm(alarmId: Long) {
        viewModelScope.launch {
            repository.delete(alarmId)
            handleScheduleResult(
                result = schedulingUseCase.cancel(alarmId),
                missingPermissionPrefix = "Alarm deleted but scheduler reported a permission issue",
            )
        }
    }

    private fun handleScheduleResult(
        result: ScheduleResult,
        missingPermissionPrefix: String,
    ) {
        val errorMessage = when (result) {
            ScheduleResult.Scheduled,
            ScheduleResult.Cancelled,
            -> null

            is ScheduleResult.MissingPermission -> {
                "$missingPermissionPrefix: ${result.permission}"
            }

            is ScheduleResult.Failed -> result.reason
        }

        mutableUiState.update { it.copy(errorMessage = errorMessage) }
    }

    private fun toUiModel(alarm: Alarm): AlarmListItemUiModel = AlarmListItemUiModel(
        id = alarm.id,
        title = alarm.title,
        nextTriggerText = formatNextTrigger(alarm.nextTriggerAtMillis),
        repeatLabel = alarm.repeatRule.toLabel(),
        enabled = alarm.enabled,
    )

    private fun formatNextTrigger(nextTriggerAtMillis: Long?): String {
        if (nextTriggerAtMillis == null) {
            return "Not scheduled"
        }

        val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())
        return formatter.format(
            Instant.ofEpochMilli(nextTriggerAtMillis).atZone(ZoneId.systemDefault()),
        )
    }

    private fun RepeatRule.toLabel(): String = when (this) {
        is RepeatRule.Once -> "Once"
        RepeatRule.Daily -> "Daily"
        RepeatRule.Weekdays -> "Weekdays"
        is RepeatRule.CustomWeekdays -> days
            .sortedBy { it.value }
            .joinToString(", ") { day ->
                day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
    }
}
