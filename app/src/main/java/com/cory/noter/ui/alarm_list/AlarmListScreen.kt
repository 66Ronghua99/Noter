package com.cory.noter.ui.alarm_list

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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    state: AlarmListUiState,
    onAlarmEnabledChanged: (Long, Boolean) -> Unit,
    onEditAlarm: (Long) -> Unit,
    onDeleteAlarm: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenManualCreate: () -> Unit,
    onOpenAiCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateSheet by remember { mutableStateOf(false) }

    if (showCreateSheet) {
        ModalBottomSheet(onDismissRequest = { showCreateSheet = false }) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Create alarm",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        showCreateSheet = false
                        onOpenManualCreate()
                    },
                ) {
                    Text(text = "Manual")
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        showCreateSheet = false
                        onOpenAiCreate()
                    },
                ) {
                    Text(text = "AI create")
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Alarms") },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text(text = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateSheet = true }) {
                Text(text = "+")
            }
        },
    ) { padding ->
        if (state.alarms.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No alarms yet",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Create one manually or ask AI to draft it for you.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = state.alarms,
                    key = { it.id },
                ) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        onAlarmEnabledChanged = onAlarmEnabledChanged,
                        onEditAlarm = onEditAlarm,
                        onDeleteAlarm = onDeleteAlarm,
                    )
                }
                item {
                    state.errorMessage?.let { errorMessage ->
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: AlarmListItemUiModel,
    onAlarmEnabledChanged: (Long, Boolean) -> Unit,
    onEditAlarm: (Long) -> Unit,
    onDeleteAlarm: (Long) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = alarm.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Next: ${alarm.nextTriggerText}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Repeat: ${alarm.repeatLabel}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = { enabled ->
                        onAlarmEnabledChanged(alarm.id, enabled)
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onEditAlarm(alarm.id) }) {
                    Text(text = "Edit")
                }
                TextButton(onClick = { onDeleteAlarm(alarm.id) }) {
                    Text(text = "Delete")
                }
            }
        }
    }
}
