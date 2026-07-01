package com.cory.noter.ui.ai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cory.noter.R

object UnifiedAiCreateTestTags {
    const val Root = "UnifiedAiCreateRoot"
    const val VoiceModeAction = "UnifiedAiCreateVoiceModeAction"
    const val TextModeAction = "UnifiedAiCreateTextModeAction"
    const val SettingsAction = "UnifiedAiCreateSettingsAction"
    const val CreateTabAction = "UnifiedAiCreateCreateTabAction"
    const val ListTabAction = "UnifiedAiCreateListTabAction"
    const val ManualCreateFabAction = "UnifiedAiCreateManualCreateFabAction"
}

private enum class UnifiedAiCreateMode {
    Voice,
    Text,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedAiCreateScreen(
    voiceContent: @Composable (onSwitchToText: () -> Unit) -> Unit,
    textContent: @Composable () -> Unit,
    onOpenAlarmList: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenManualCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(UnifiedAiCreateMode.Voice) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(UnifiedAiCreateTestTags.Root),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(
                        modifier = Modifier.testTag(UnifiedAiCreateTestTags.SettingsAction),
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
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.testTag(UnifiedAiCreateTestTags.ManualCreateFabAction),
                onClick = onOpenManualCreate,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.ai_create_manual_instead),
                )
            }
        },
        bottomBar = {
            BottomCreateListBar(
                selectedCreate = true,
                onCreateClick = {},
                onListClick = onOpenAlarmList,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PageHeader()

            SegmentedModeControl(
                selectedMode = mode,
                onModeSelected = { mode = it },
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                AnimatedContent(
                    targetState = mode,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 160))
                    },
                    label = "UnifiedAiCreateModeTransition",
                ) { targetMode ->
                    when (targetMode) {
                        UnifiedAiCreateMode.Voice -> voiceContent {
                            mode = UnifiedAiCreateMode.Text
                        }

                        UnifiedAiCreateMode.Text -> textContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomCreateListBar(
    selectedCreate: Boolean,
    onCreateClick: () -> Unit,
    onListClick: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedCreate,
            onClick = onCreateClick,
            icon = {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            label = { Text(text = stringResource(R.string.ai_create_bottom_create)) },
            modifier = Modifier.testTag(UnifiedAiCreateTestTags.CreateTabAction),
        )
        NavigationBarItem(
            selected = !selectedCreate,
            onClick = onListClick,
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            label = { Text(text = stringResource(R.string.alarm_list_title)) },
            modifier = Modifier.testTag(UnifiedAiCreateTestTags.ListTabAction),
        )
    }
}

@Composable
private fun PageHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.ai_create_page_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.ai_create_page_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SegmentedModeControl(
    selectedMode: UnifiedAiCreateMode,
    onModeSelected: (UnifiedAiCreateMode) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModeOption(
                icon = Icons.Default.Mic,
                label = stringResource(R.string.ai_create_mode_voice),
                selected = selectedMode == UnifiedAiCreateMode.Voice,
                modifier = Modifier
                    .weight(1f)
                    .testTag(UnifiedAiCreateTestTags.VoiceModeAction),
                onClick = { onModeSelected(UnifiedAiCreateMode.Voice) },
            )
            ModeOption(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = stringResource(R.string.ai_create_mode_text),
                selected = selectedMode == UnifiedAiCreateMode.Text,
                modifier = Modifier
                    .weight(1f)
                    .testTag(UnifiedAiCreateTestTags.TextModeAction),
                onClick = { onModeSelected(UnifiedAiCreateMode.Text) },
            )
        }
    }
}

@Composable
private fun ModeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = if (selected) 2.dp else 0.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
