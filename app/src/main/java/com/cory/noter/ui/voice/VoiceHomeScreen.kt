package com.cory.noter.ui.voice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
                    IconButton(
                        modifier = Modifier.testTag(VoiceHomeTestTags.ListAction),
                        onClick = onOpenAlarmList,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ListAlt,
                            contentDescription = stringResource(R.string.voice_home_open_list),
                        )
                    }
                    IconButton(
                        modifier = Modifier.testTag(VoiceHomeTestTags.SettingsAction),
                        onClick = onOpenSettings,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
            )
        },
    ) { padding ->
        VoiceModeContent(
            state = state,
            onRecordPressed = onRecordPressed,
            onRecordReleased = onRecordReleased,
            onRecordCancelled = onRecordCancelled,
            onRetry = onRetry,
            onOpenPermissionSettings = onOpenPermissionSettings,
            onOpenTextInput = onOpenTextInput,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

@Composable
fun VoiceModeContent(
    state: VoiceHomeUiState,
    onRecordPressed: () -> Unit,
    onRecordReleased: () -> Unit,
    onRecordCancelled: () -> Unit,
    onRetry: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onOpenTextInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        VoiceStatusChip(state = state)

        VoiceRecordButton(
            status = state.status,
            onRecordPressed = onRecordPressed,
            onRecordReleased = onRecordReleased,
            onRecordCancelled = onRecordCancelled,
        )

        VoiceHint(state = state)

        VoiceModeActions(
            state = state,
            onRetry = onRetry,
            onOpenPermissionSettings = onOpenPermissionSettings,
            onOpenTextInput = onOpenTextInput,
            onRecordCancelled = onRecordCancelled,
        )
    }
}

@Composable
private fun VoiceStatusChip(state: VoiceHomeUiState) {
    val (containerColor, contentColor, label, iconVector) = when {
        state.status == VoiceHomeStatus.Recording -> quadOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            stringResource(R.string.voice_home_status_listening),
            Icons.Default.Mic,
        )

        state.status == VoiceHomeStatus.Processing -> quadOf(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            stringResource(R.string.voice_home_status_processing),
            Icons.Default.Mic,
        )

        state.noticeMessage != null -> quadOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            state.noticeMessage.asString(),
            Icons.Default.Mic,
        )

        state.errorMessage != null -> quadOf(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            state.errorMessage.asString(),
            Icons.Default.Mic,
        )

        else -> quadOf(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.voice_home_status_ready),
            Icons.Default.Mic,
        )
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        border = if (state.status == VoiceHomeStatus.Recording ||
            state.status == VoiceHomeStatus.Processing ||
            state.noticeMessage != null ||
            state.errorMessage != null
        ) {
            null
        } else {
            androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun <A, B, C, D> quadOf(a: A, b: B, c: C, d: D): VoiceChipColors<A, B, C, D> =
    VoiceChipColors(a, b, c, d)

private data class VoiceChipColors<A, B, C, D>(
    val container: A,
    val content: B,
    val label: C,
    val icon: D,
)

@Composable
private fun VoiceHint(state: VoiceHomeUiState) {
    val text = when {
        state.status == VoiceHomeStatus.Recording -> stringResource(R.string.voice_home_recording_prompt)
        state.status == VoiceHomeStatus.Processing -> stringResource(R.string.voice_home_processing_notice)
        state.status == VoiceHomeStatus.PermissionNeeded -> stringResource(R.string.voice_home_permission_needed)
        state.errorMessage != null -> state.errorMessage.asString()
        state.noticeMessage != null -> state.noticeMessage.asString()
        else -> stringResource(R.string.voice_home_idle_prompt)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .testTag(
                when {
                    state.noticeMessage != null -> VoiceHomeTestTags.Notice
                    state.errorMessage != null -> VoiceHomeTestTags.Error
                    else -> VoiceHomeTestTags.Notice
                },
            ),
    )
}

@Composable
private fun VoiceRecordButton(
    status: VoiceHomeStatus,
    onRecordPressed: () -> Unit,
    onRecordReleased: () -> Unit,
    onRecordCancelled: () -> Unit,
) {
    val currentOnRecordPressed by rememberUpdatedState(onRecordPressed)
    val currentOnRecordReleased by rememberUpdatedState(onRecordReleased)
    val currentOnRecordCancelled by rememberUpdatedState(onRecordCancelled)

    val isRecording = status == VoiceHomeStatus.Recording
    val containerColor = when (status) {
        VoiceHomeStatus.Recording -> MaterialTheme.colorScheme.primaryContainer
        VoiceHomeStatus.Processing -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when (status) {
        VoiceHomeStatus.Recording -> MaterialTheme.colorScheme.onPrimaryContainer
        VoiceHomeStatus.Processing -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Surface(
        modifier = Modifier
            .size(132.dp)
            .testTag(VoiceHomeTestTags.RecordButton)
            .semantics { role = Role.Button }
            .pointerInput(
                voiceRecordPointerInputKey(
                    onRecordPressed = onRecordPressed,
                    onRecordReleased = onRecordReleased,
                    onRecordCancelled = onRecordCancelled,
                ),
            ) {
                detectTapGestures(
                    onPress = {
                        currentOnRecordPressed()
                        handleRecordPressCompletion(
                            wasReleased = tryAwaitRelease(),
                            onRecordReleased = currentOnRecordReleased,
                            onRecordCancelled = currentOnRecordCancelled,
                        )
                    },
                )
            },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (isRecording) 0.dp else 4.dp,
        shadowElevation = if (isRecording) 0.dp else 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = status,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith
                        fadeOut(animationSpec = tween(150))
                },
                label = "VoiceRecordButtonIcon",
            ) { targetStatus ->
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = when (targetStatus) {
                        VoiceHomeStatus.Recording -> stringResource(R.string.voice_home_recording_button)
                        VoiceHomeStatus.Processing -> stringResource(R.string.voice_home_processing_button)
                        else -> stringResource(R.string.voice_home_hold_button)
                    },
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun voiceRecordPointerInputKey(
    onRecordPressed: () -> Unit,
    onRecordReleased: () -> Unit,
    onRecordCancelled: () -> Unit,
): Any = VoiceRecordPointerInputKey

private object VoiceRecordPointerInputKey

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
private fun VoiceModeActions(
    state: VoiceHomeUiState,
    onRetry: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onOpenTextInput: () -> Unit,
    onRecordCancelled: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.showRetryAction) {
            Card(
                modifier = Modifier.testTag(VoiceHomeTestTags.RetryAction),
                onClick = onRetry,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = stringResource(R.string.voice_home_retry),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (state.showPermissionRecoveryAction) {
            Card(
                modifier = Modifier.testTag(VoiceHomeTestTags.PermissionRecoveryAction),
                onClick = onOpenPermissionSettings,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = stringResource(R.string.voice_home_permission_action),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (state.showTextFallbackAction) {
            Card(
                modifier = Modifier.testTag(VoiceHomeTestTags.TextFallbackAction),
                onClick = onOpenTextInput,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = stringResource(R.string.voice_home_text_fallback),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (state.status == VoiceHomeStatus.Recording) {
            Card(
                modifier = Modifier.testTag(VoiceHomeTestTags.CancelAction),
                onClick = onRecordCancelled,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = stringResource(R.string.voice_home_cancel),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
