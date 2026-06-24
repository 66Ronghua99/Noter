package com.cory.noter.ui.voice

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cory.noter.R
import com.cory.noter.ui.text.asString

object VoiceHomeTestTags {
    const val RecordButton = "VoiceHomeRecordButton"
    const val Notice = "VoiceHomeNotice"
    const val Error = "VoiceHomeError"
    const val CancelAction = "VoiceHomeCancelAction"
    const val RetryAction = "VoiceHomeRetryAction"
    const val PermissionRecoveryAction = "VoiceHomePermissionRecoveryAction"
    const val TextFallbackAction = "VoiceHomeTextFallbackAction"
    const val ListAction = "VoiceHomeListAction"
    const val SettingsAction = "VoiceHomeSettingsAction"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceHomeScreen(
    state: VoiceHomeUiState,
    onRecordPressed: () -> Unit,
    onRecordReleased: () -> Unit,
    onRecordCancelled: () -> Unit,
    onRetry: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onOpenTextInput: () -> Unit,
    onOpenAlarmList: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.voice_home_title)) },
                actions = {
                    TextButton(
                        modifier = Modifier.testTag(VoiceHomeTestTags.ListAction),
                        onClick = onOpenAlarmList,
                    ) {
                        Text(text = stringResource(R.string.voice_home_open_list))
                    }
                    TextButton(
                        modifier = Modifier.testTag(VoiceHomeTestTags.SettingsAction),
                        onClick = onOpenSettings,
                    ) {
                        Text(text = stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            VoiceHomeMessageBand(state = state)

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                VoiceRecordButton(
                    status = state.status,
                    onRecordPressed = onRecordPressed,
                    onRecordReleased = onRecordReleased,
                    onRecordCancelled = onRecordCancelled,
                )

                if (state.status == VoiceHomeStatus.Recording) {
                    TextButton(
                        modifier = Modifier.testTag(VoiceHomeTestTags.CancelAction),
                        onClick = onRecordCancelled,
                    ) {
                        Text(text = stringResource(R.string.voice_home_cancel))
                    }
                }
            }

            VoiceHomeActions(
                state = state,
                onRetry = onRetry,
                onOpenPermissionSettings = onOpenPermissionSettings,
                onOpenTextInput = onOpenTextInput,
            )
        }
    }
}

@Composable
private fun VoiceHomeMessageBand(state: VoiceHomeUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val notice = state.noticeMessage
        val error = state.errorMessage
        if (notice != null) {
            Text(
                text = notice.asString(),
                modifier = Modifier.testTag(VoiceHomeTestTags.Notice),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        } else if (error != null) {
            Text(
                text = error.asString(),
                modifier = Modifier.testTag(VoiceHomeTestTags.Error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = when (state.status) {
                    VoiceHomeStatus.Idle -> stringResource(R.string.voice_home_idle_prompt)
                    VoiceHomeStatus.Recording -> stringResource(R.string.voice_home_recording_prompt)
                    VoiceHomeStatus.Processing -> stringResource(R.string.voice_home_processing_notice)
                    VoiceHomeStatus.PermissionNeeded -> stringResource(R.string.voice_home_permission_needed)
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun VoiceRecordButton(
    status: VoiceHomeStatus,
    onRecordPressed: () -> Unit,
    onRecordReleased: () -> Unit,
    onRecordCancelled: () -> Unit,
) {
    val containerColor = when (status) {
        VoiceHomeStatus.Recording -> MaterialTheme.colorScheme.tertiaryContainer
        VoiceHomeStatus.Processing -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (status) {
        VoiceHomeStatus.Recording -> MaterialTheme.colorScheme.onTertiaryContainer
        VoiceHomeStatus.Processing -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        modifier = Modifier
            .size(196.dp)
            .testTag(VoiceHomeTestTags.RecordButton)
            .semantics { role = Role.Button }
            .pointerInput(onRecordPressed, onRecordReleased, onRecordCancelled) {
                detectTapGestures(
                    onPress = {
                        onRecordPressed()
                        handleRecordPressCompletion(
                            wasReleased = tryAwaitRelease(),
                            onRecordReleased = onRecordReleased,
                            onRecordCancelled = onRecordCancelled,
                        )
                    },
                )
            },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = when (status) {
                    VoiceHomeStatus.Recording -> stringResource(R.string.voice_home_recording_button)
                    VoiceHomeStatus.Processing -> stringResource(R.string.voice_home_processing_button)
                    else -> stringResource(R.string.voice_home_hold_button)
                },
                modifier = Modifier.padding(20.dp),
                color = contentColor,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal fun handleRecordPressCompletion(
    wasReleased: Boolean,
    onRecordReleased: () -> Unit,
    onRecordCancelled: () -> Unit,
) {
    if (wasReleased) {
        onRecordReleased()
    } else {
        onRecordCancelled()
    }
}

@Composable
private fun VoiceHomeActions(
    state: VoiceHomeUiState,
    onRetry: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onOpenTextInput: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.showRetryAction) {
            Button(
                modifier = Modifier.testTag(VoiceHomeTestTags.RetryAction),
                onClick = onRetry,
            ) {
                Text(text = stringResource(R.string.voice_home_retry))
            }
        }
        if (state.showPermissionRecoveryAction) {
            Button(
                modifier = Modifier.testTag(VoiceHomeTestTags.PermissionRecoveryAction),
                onClick = onOpenPermissionSettings,
            ) {
                Text(text = stringResource(R.string.voice_home_permission_action))
            }
        }
        if (state.showTextFallbackAction) {
            TextButton(
                modifier = Modifier.testTag(VoiceHomeTestTags.TextFallbackAction),
                onClick = onOpenTextInput,
            ) {
                Text(text = stringResource(R.string.voice_home_text_fallback))
            }
        }
    }
}
