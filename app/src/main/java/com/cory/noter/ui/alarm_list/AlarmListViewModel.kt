package com.cory.noter.ui.alarm_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cory.noter.R
import com.cory.noter.alarm.AlarmManagementResult
import com.cory.noter.alarm.AlarmManagementUseCase
import com.cory.noter.alarm.AlarmSchedulingUseCase
import com.cory.noter.alarm.ScheduleResult
import com.cory.noter.data.alarm.AlarmRepository
import com.cory.noter.domain.alarm.Alarm
import com.cory.noter.domain.alarm.AlarmPauseMode
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
    val pauseChoiceDialog: AlarmPauseChoiceDialogUiModel? = null,
    val deleteConfirmDialog: AlarmDeleteConfirmDialogUiModel? = null,
    val errorMessage: UiText? = null,
)

data class AlarmPauseChoiceDialogUiModel(
    val alarmId: Long,
    val alarmTitle: String,
)

data class AlarmDeleteConfirmDialogUiModel(
    val alarmId: Long,
    val alarmTitle: String,
)

sealed interface AlarmPauseStatusUiModel {
    data class PausedNext(val pausedOccurrenceAtMillis: Long) : AlarmPauseStatusUiModel
    data object PausedIndefinitely : AlarmPauseStatusUiModel
}

data class AlarmListItemUiModel(
    val id: Long,
    val title: String,
    val nextTriggerAtMillis: Long?,
    val repeatRule: RepeatRule,
    val enabled: Boolean,
    val pauseStatus: AlarmPauseStatusUiModel?,
)

class AlarmListViewModel(
    private val repository: AlarmRepository,
    private val schedulingUseCase: AlarmSchedulingUseCase,
    private val managementUseCase: AlarmManagementUseCase,
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
            val alarm = repository.get(alarmId) ?: return@launch
            if (enabled) {
                if (alarm.pauseMode == AlarmPauseMode.NONE) {
                    enableLegacyDisabledAlarm(alarmId)
                } else {
                    handleManagementResult(managementUseCase.resume(alarmId))
                }
            } else {
                if (alarm.pauseMode == AlarmPauseMode.NONE && alarm.enabled) {
                    mutableUiState.update {
                        it.copy(
                            pauseChoiceDialog = AlarmPauseChoiceDialogUiModel(
                                alarmId = alarm.id,
                                alarmTitle = alarm.title,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun onConfirmPauseNextOccurrence() {
        val alarmId = mutableUiState.value.pauseChoiceDialog?.alarmId ?: return
        viewModelScope.launch {
            handleManagementResult(managementUseCase.pauseNextOccurrence(alarmId))
        }
    }

    fun onConfirmPauseIndefinitely() {
        val alarmId = mutableUiState.value.pauseChoiceDialog?.alarmId ?: return
        viewModelScope.launch {
            handleManagementResult(managementUseCase.pauseIndefinitely(alarmId))
        }
    }

    fun onCancelPauseChoice() {
        mutableUiState.update { it.copy(pauseChoiceDialog = null) }
    }

    fun onDeleteAlarmRequested(alarmId: Long) {
        viewModelScope.launch {
            val alarm = repository.get(alarmId) ?: return@launch
            mutableUiState.update {
                it.copy(
                    deleteConfirmDialog = AlarmDeleteConfirmDialogUiModel(
                        alarmId = alarm.id,
                        alarmTitle = alarm.title,
                    ),
                )
            }
        }
    }

    fun onCancelDeleteConfirmation() {
        mutableUiState.update { it.copy(deleteConfirmDialog = null) }
    }

    fun onConfirmDeleteAlarm() {
        val alarmId = mutableUiState.value.deleteConfirmDialog?.alarmId ?: return
        viewModelScope.launch {
            repository.delete(alarmId)
            handleScheduleResult(
                result = schedulingUseCase.cancel(alarmId),
                missingPermissionResId = R.string.alarm_list_schedule_permission_deleted_error,
            )
        }
    }

    private suspend fun enableLegacyDisabledAlarm(alarmId: Long) {
        val updatedAlarm = repository.enable(alarmId) ?: return
        handleScheduleResult(
            result = schedulingUseCase.syncSchedule(updatedAlarm),
            missingPermissionResId = R.string.alarm_list_schedule_permission_saved_error,
        )
    }

    private fun handleManagementResult(result: AlarmManagementResult) {
        val errorMessage = when (result) {
            is AlarmManagementResult.Updated -> null
            AlarmManagementResult.MissingAlarm -> UiText.Resource(R.string.alarm_list_management_missing_alarm)
            is AlarmManagementResult.InvalidState -> UiText.Raw(result.reason)
            is AlarmManagementResult.MissingSchedulingPermission -> {
                UiText.Resource(R.string.alarm_list_schedule_permission_saved_error, listOf(result.permission))
            }

            is AlarmManagementResult.SchedulerFailed -> UiText.Raw(result.reason)
            is AlarmManagementResult.StaleOrMismatchedTrigger -> {
                UiText.Resource(R.string.alarm_list_management_stale_trigger)
            }
        }
        mutableUiState.update {
            it.copy(
                pauseChoiceDialog = if (errorMessage == null) null else it.pauseChoiceDialog,
                errorMessage = errorMessage,
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

        mutableUiState.update {
            it.copy(
                deleteConfirmDialog = null,
                errorMessage = errorMessage,
            )
        }
    }

    private fun toUiModel(alarm: Alarm): AlarmListItemUiModel = AlarmListItemUiModel(
        id = alarm.id,
        title = alarm.title,
        nextTriggerAtMillis = alarm.nextTriggerAtMillis,
        repeatRule = alarm.repeatRule,
        enabled = alarm.enabled && alarm.pauseMode == AlarmPauseMode.NONE,
        pauseStatus = alarm.toPauseStatusUiModel(),
    )

    private fun Alarm.toPauseStatusUiModel(): AlarmPauseStatusUiModel? = when (pauseMode) {
        AlarmPauseMode.NONE -> null
        AlarmPauseMode.NEXT_OCCURRENCE -> pausedOccurrenceAtMillis?.let(AlarmPauseStatusUiModel::PausedNext)
        AlarmPauseMode.INDEFINITE -> AlarmPauseStatusUiModel.PausedIndefinitely
    }
}
