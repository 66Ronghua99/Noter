package com.cory.noter.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cory.noter.R
import com.cory.noter.ui.text.asString

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
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.ai_create_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.ai_create_selected_model),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = state.selectedModelId.ifBlank {
                    stringResource(R.string.ai_create_no_model_selected)
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = state.prompt,
                onValueChange = onPromptChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text(text = stringResource(R.string.ai_create_prompt_label)) },
            )

            state.errorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage.asString(),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.statusMessage?.let { statusMessage ->
                Text(
                    text = statusMessage.asString(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.exactAlarmPermissionRequired) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenExactAlarmSettings,
                ) {
                    Text(text = stringResource(R.string.ai_create_exact_alarm_settings))
                }
            }

            if (state.isLoading) {
                CircularProgressIndicator()
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSubmit,
                enabled = !state.isLoading,
            ) {
                Text(text = stringResource(R.string.ai_create_submit))
            }
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenManualCreate,
            ) {
                Text(text = stringResource(R.string.ai_create_manual_instead))
            }
        }
    }
}
