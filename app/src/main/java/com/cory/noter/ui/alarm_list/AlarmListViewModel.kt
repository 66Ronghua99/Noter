package com.cory.noter.ui.alarm_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cory.noter.R
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.ScheduleResult
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.RepeatRule
import com.cory.noter.ui.text.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlarmListUiState(
    val alarms: List<AlarmListItemUiModel> = emptyList(),
    val errorMessage: UiText? = null,
)

data class AlarmListItemUiModel(
    val id: Long,
    val title: String,
    val nextTriggerAtMillis: Long?,
    val repeatRule: RepeatRule,
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
                missingPermissionResId = R.string.alarm_list_schedule_permission_saved_error,
            )
        }
    }

    fun onDeleteAlarm(alarmId: Long) {
        viewModelScope.launch {
            repository.delete(alarmId)
            handleScheduleResult(
                result = schedulingUseCase.cancel(alarmId),
                missingPermissionResId = R.string.alarm_list_schedule_permission_deleted_error,
            )
        }
    }

    private fun handleScheduleResult(
        result: ScheduleResult,
        missingPermissionResId: Int,
    ) {
        val errorMessage = when (result) {
            ScheduleResult.Scheduled,
            ScheduleResult.Cancelled,
            -> null

            is ScheduleResult.MissingPermission -> {
                UiText.Resource(missingPermissionResId, listOf(result.permission))
            }

            is ScheduleResult.Failed -> UiText.Raw(result.reason)
        }

        mutableUiState.update { it.copy(errorMessage = errorMessage) }
    }

    private fun toUiModel(alarm: Alarm): AlarmListItemUiModel = AlarmListItemUiModel(
        id = alarm.id,
        title = alarm.title,
        nextTriggerAtMillis = alarm.nextTriggerAtMillis,
        repeatRule = alarm.repeatRule,
        enabled = alarm.enabled,
    )
}
