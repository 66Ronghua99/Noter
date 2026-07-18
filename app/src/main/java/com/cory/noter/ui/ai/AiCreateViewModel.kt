package com.cory.noter.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cory.noter.ai.AiAlarmManagementAction
import com.cory.noter.ai.AiAlarmCreator
import com.cory.noter.ai.AiCreateBackgroundScheduler
import com.cory.noter.ai.AiCreateResult
import com.cory.noter.ai.AiCalendarSyncStatus
import com.cory.noter.ai.AiListedAlarmFormatter
import com.cory.noter.R
import com.cory.noter.ui.text.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiCreateUiState(
    val prompt: String = "",
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null,
    val statusMessage: UiText? = null,
    val exactAlarmPermissionRequired: Boolean = false,
    val createdAlarmId: Long? = null,
)

class AiCreateViewModel(
    private val creator: AiAlarmCreator,
    private val backgroundScheduler: AiCreateBackgroundScheduler? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AiCreateUiState())
    val uiState: StateFlow<AiCreateUiState> = mutableUiState.asStateFlow()

    fun onPromptChanged(prompt: String) {
        mutableUiState.update {
            it.copy(
                prompt = prompt,
                errorMessage = null,
                statusMessage = null,
                exactAlarmPermissionRequired = false,
            )
        }
    }

    fun submit() {
        val prompt = uiState.value.prompt.trim()
        if (prompt.isEmpty()) {
            mutableUiState.update {
                it.copy(errorMessage = UiText.Resource(R.string.ai_create_empty_prompt_error))
            }
            return
        }

        val scheduler = backgroundScheduler
        if (scheduler != null) {
            scheduler.enqueue(prompt)
            mutableUiState.update {
                it.copy(
                    prompt = "",
                    isLoading = false,
                    errorMessage = null,
                    statusMessage = UiText.Resource(R.string.ai_create_background_status),
                    exactAlarmPermissionRequired = false,
                )
            }
            return
        }

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = null,
                    exactAlarmPermissionRequired = false,
                )
            }

            val result = creator.createFromText(prompt)
            mutableUiState.update { current ->
                current.copy(
                    isLoading = false,
                    errorMessage = result.toErrorMessage(),
                    statusMessage = result.toStatusMessage(),
                    exactAlarmPermissionRequired = result is AiCreateResult.MissingSchedulingPermission,
                    createdAlarmId = (result as? AiCreateResult.Created)?.alarm?.id,
                )
            }
        }
    }

    private fun AiCreateResult.toErrorMessage(): UiText? = when (this) {
        is AiCreateResult.NetworkFailure,
        is AiCreateResult.RateLimited,
        is AiCreateResult.RemoteFailure,
        -> UiText.Resource(R.string.ai_create_service_unavailable_error)
        is AiCreateResult.ServiceFailure -> UiText.Resource(
            if (code == "invalid_service_response") {
                R.string.ai_create_update_required_error
            } else {
                R.string.ai_create_service_unavailable_error
            },
        )
        is AiCreateResult.InvalidResponse -> UiText.Resource(R.string.ai_create_update_required_error)
        is AiCreateResult.ClarificationRequired -> UiText.Raw(reason)
        is AiCreateResult.CreateFailed -> UiText.Raw(reason)
        is AiCreateResult.MissingSchedulingPermission ->
            UiText.Resource(R.string.ai_create_missing_permission_error, listOf(permission))

        is AiCreateResult.ScheduleFailed -> UiText.Raw(reason)
        is AiCreateResult.ManagementSucceeded -> null
        is AiCreateResult.AlarmsListed -> null
        is AiCreateResult.Created -> null
    }

    private fun AiCreateResult.toStatusMessage(): UiText? = when (this) {
        is AiCreateResult.AlarmsListed ->
            UiText.Raw(AiListedAlarmFormatter.formatStatus(alarms))

        is AiCreateResult.ManagementSucceeded -> when (action) {
            AiAlarmManagementAction.PAUSED_NEXT_OCCURRENCE ->
                UiText.Resource(R.string.ai_create_management_paused_next_status, listOf(alarm.title))

            AiAlarmManagementAction.PAUSED_INDEFINITELY ->
                UiText.Resource(R.string.ai_create_management_paused_indefinitely_status, listOf(alarm.title))

            AiAlarmManagementAction.RESUMED ->
                UiText.Resource(R.string.ai_create_management_resumed_status, listOf(alarm.title))
        }

        is AiCreateResult.Created -> when (calendarSync.status) {
            AiCalendarSyncStatus.SYNCED ->
                UiText.Resource(R.string.ai_create_created_synced_status, listOf(alarm.title))

            AiCalendarSyncStatus.SKIPPED ->
                UiText.Resource(R.string.ai_create_created_calendar_skipped_status, listOf(alarm.title))

            AiCalendarSyncStatus.MISSING_CALENDAR_PERMISSION ->
                UiText.Resource(
                    R.string.ai_create_created_calendar_missing_permission_status,
                    listOf(alarm.title),
                )

            AiCalendarSyncStatus.MISSING_DEFAULT_CALENDAR ->
                UiText.Resource(
                    R.string.ai_create_created_calendar_missing_default_status,
                    listOf(alarm.title),
                )

            AiCalendarSyncStatus.CALENDAR_NOT_WRITABLE ->
                UiText.Resource(
                    R.string.ai_create_created_calendar_not_writable_status,
                    listOf(alarm.title),
                )

            AiCalendarSyncStatus.CALENDAR_INSERT_FAILED ->
                toCalendarFailureStatus(R.string.ai_create_created_calendar_insert_failed_status)

            AiCalendarSyncStatus.MAPPING_PERSIST_FAILED ->
                toCalendarFailureStatus(R.string.ai_create_created_calendar_mapping_failed_status)
        }

        else -> null
    }

    private fun AiCreateResult.Created.toCalendarFailureStatus(genericMessageRes: Int): UiText {
        if (calendarSync.hasFailureReason) {
            return UiText.Resource(
                R.string.ai_create_created_calendar_failed_status,
                listOf(alarm.title, calendarSync.failureLabel),
            )
        }
        return UiText.Resource(genericMessageRes, listOf(alarm.title))
    }
}
