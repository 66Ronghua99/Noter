package com.cory.noter.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onApiKeyChanged: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onModelSelected: (String) -> Unit,
    onPickDefaultRingtone: () -> Unit,
    onPermissionAction: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "OpenRouter API key",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = state.openRouterApiKey,
                        onValueChange = onApiKeyChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "API key") },
                    )
                    Button(onClick = onSaveApiKey) {
                        Text(text = "Save API key")
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Model",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    state.modelOptions.forEach { modelId ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onModelSelected(modelId) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.selectedModelId == modelId,
                                onClick = { onModelSelected(modelId) },
                            )
                            Text(text = modelId)
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Default ringtone",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.defaultRingtoneUri,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = onPickDefaultRingtone) {
                        Text(text = "Choose default ringtone")
                    }
                }
            }

            item {
                Text(
                    text = "Permissions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(state.permissionRows, key = { it.id }) { row ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (row.granted) "Granted" else "Needs attention",
                            color = if (row.granted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                        Text(
                            text = row.summary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!row.granted && row.actionLabel != null) {
                            Button(onClick = { onPermissionAction(row.id) }) {
                                Text(text = row.actionLabel)
                            }
                        }
                    }
                }
            }

            item {
                state.errorMessage?.let { errorMessage ->
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
