package com.cory.noter.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cory.noter.R
import com.cory.noter.ui.text.asString

object AiCreateTestTags {
    const val Root = "AiCreateRoot"
    const val PromptInput = "AiCreatePromptInput"
    const val SubmitAction = "AiCreateSubmitAction"
    const val ManualCreateAction = "AiCreateManualCreateAction"
    const val ExactAlarmAction = "AiCreateExactAlarmAction"
    const val LoadingIndicator = "AiCreateLoadingIndicator"
    const val StatusMessage = "AiCreateStatusMessage"
    const val ErrorMessage = "AiCreateErrorMessage"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCreateScreen(
    state: AiCreateUiState,
    onPromptChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenManualCreate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(AiCreateTestTags.Root),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.ai_create_title)) },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                    ) {
                        Text(text = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        TextModeContent(
            state = state,
            onPromptChanged = onPromptChanged,
            onSubmit = onSubmit,
            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
            onOpenManualCreate = onOpenManualCreate,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        )
    }
}

@Composable
fun TextModeContent(
    state: AiCreateUiState,
    onPromptChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenManualCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .testTag(AiCreateTestTags.Root),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = onPromptChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .testTag(AiCreateTestTags.PromptInput),
                    placeholder = { Text(text = stringResource(R.string.ai_create_prompt_placeholder)) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = MaterialTheme.shapes.medium,
                )

                SuggestionChips(
                    suggestions = listOf(
                        stringResource(R.string.ai_create_suggestion_morning),
                        stringResource(R.string.ai_create_suggestion_workday),
                        stringResource(R.string.ai_create_suggestion_monday),
                    ),
                    onSuggestionClicked = { onPromptChanged(it) },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { onPromptChanged("") },
                        enabled = state.prompt.isNotBlank() && !state.isLoading,
                    ) {
                        Text(text = stringResource(R.string.common_clear))
                    }
                    Button(
                        modifier = Modifier.testTag(AiCreateTestTags.SubmitAction),
                        onClick = onSubmit,
                        enabled = state.prompt.isNotBlank() && !state.isLoading,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(text = stringResource(R.string.ai_create_submit))
                    }
                }
            }
        }

        InlineStatus(state = state)

        if (state.exactAlarmPermissionRequired) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AiCreateTestTags.ExactAlarmAction),
                onClick = onOpenExactAlarmSettings,
            ) {
                Text(text = stringResource(R.string.ai_create_exact_alarm_settings))
            }
        }

        TextButton(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AiCreateTestTags.ManualCreateAction),
            onClick = onOpenManualCreate,
        ) {
            Text(text = stringResource(R.string.ai_create_manual_instead))
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SuggestionChips(
    suggestions: List<String>,
    onSuggestionClicked: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { suggestion ->
            Card(
                onClick = { onSuggestionClicked(suggestion) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Text(
                    text = suggestion,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun InlineStatus(state: AiCreateUiState) {
    val (visible, isError, message) = when {
        state.isLoading -> Triple(true, false, stringResource(R.string.ai_create_status_creating))
        state.errorMessage != null -> Triple(true, true, state.errorMessage.asString())
        state.statusMessage != null -> Triple(true, false, state.statusMessage.asString())
        else -> Triple(false, false, "")
    }

    if (!visible) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .testTag(AiCreateTestTags.LoadingIndicator),
                    strokeWidth = 3.dp,
                )
            }
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(18.dp)
                        .testTag(AiCreateTestTags.LoadingIndicator),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = message,
                modifier = if (isError) {
                    Modifier.testTag(AiCreateTestTags.ErrorMessage)
                } else {
                    Modifier.testTag(AiCreateTestTags.StatusMessage)
                },
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
