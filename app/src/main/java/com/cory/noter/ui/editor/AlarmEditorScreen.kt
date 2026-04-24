package com.cory.noter.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
fun AlarmEditorScreen(
    state: AlarmEditorUiState,
    onTitleChanged: (String) -> Unit,
    onHourChanged: (String) -> Unit,
    onMinuteChanged: (String) -> Unit,
    onRepeatRuleChanged: (EditorRepeatOption) -> Unit,
    onOnceDateChanged: (String) -> Unit,
    onCustomWeekdayToggled: (DayOfWeek) -> Unit,
    onPickRingtone: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repeatOptions = listOf(
        EditorRepeatOption.ONCE to "Once",
        EditorRepeatOption.DAILY to "Daily",
        EditorRepeatOption.WEEKDAYS to "Weekdays",
        EditorRepeatOption.CUSTOM to "Custom",
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = if (state.isExisting) "Edit alarm" else "New alarm")
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Title") },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.hourText,
                    onValueChange = onHourChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text(text = "Hour") },
                )
                OutlinedTextField(
                    value = state.minuteText,
                    onValueChange = onMinuteChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text(text = "Minute") },
                )
            }

            Text(
                text = "Repeat",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeatOptions.forEach { (option, label) ->
                    FilterChip(
                        selected = state.repeatOption == option,
                        onClick = { onRepeatRuleChanged(option) },
                        label = { Text(text = label) },
                    )
                }
            }

            if (state.repeatOption == EditorRepeatOption.ONCE) {
                OutlinedTextField(
                    value = state.onceDateText,
                    onValueChange = onOnceDateChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Date (YYYY-MM-DD)") },
                )
            }

            if (state.repeatOption == EditorRepeatOption.CUSTOM) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in state.customWeekdays,
                            onClick = { onCustomWeekdayToggled(day) },
                            label = {
                                Text(
                                    text = day.getDisplayName(
                                        TextStyle.SHORT,
                                        Locale.getDefault(),
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            Text(
                text = "Ringtone",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = state.ringtoneUri,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onPickRingtone) {
                Text(text = "Choose ringtone")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Enabled")
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onEnabledChanged,
                )
            }

            state.validationErrors.forEach { error ->
                Text(
                    text = error.name,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.errorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSave) {
                    Text(text = "Save")
                }
                if (state.isExisting) {
                    TextButton(onClick = onDelete) {
                        Text(text = "Delete")
                    }
                }
            }
        }
    }
}
