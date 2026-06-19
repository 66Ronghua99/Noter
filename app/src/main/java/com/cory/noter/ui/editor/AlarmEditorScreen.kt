package com.cory.noter.ui.editor

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
fun AlarmEditorScreen(
    state: AlarmEditorUiState,
    onTitleChanged: (String) -> Unit,
    onHourSelected: (Int) -> Unit,
    onMinuteSelected: (Int) -> Unit,
    onRepeatRuleChanged: (EditorRepeatOption) -> Unit,
    onOnceDateSelected: (LocalDate) -> Unit,
    onIntervalStartDateSelected: (LocalDate) -> Unit,
    onIntervalEndDateSelected: (LocalDate) -> Unit,
    onIntervalWeeksSelected: (Int) -> Unit,
    onCustomWeekdayToggled: (DayOfWeek) -> Unit,
    onPickRingtone: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        configuration.locale
    }
    val repeatOptions = listOf(
        EditorRepeatOption.ONCE to "Once",
        EditorRepeatOption.DAILY to "Daily",
        EditorRepeatOption.WEEKDAYS to "Weekdays",
        EditorRepeatOption.CUSTOM to "Custom",
        EditorRepeatOption.INTERVAL to "Interval",
    )
    var activeDatePicker by remember { mutableStateOf<DatePickerTarget?>(null) }

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                NumberWheelPicker(
                    label = "Hours",
                    values = 0..23,
                    selectedValue = state.hourText.toIntOrNull()?.coerceIn(0, 23) ?: 0,
                    onValueSelected = onHourSelected,
                    tagPrefix = "HourWheel",
                    displayText = { it.toString().padStart(2, '0') },
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                NumberWheelPicker(
                    label = "Minutes",
                    values = 0..59,
                    selectedValue = state.minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0,
                    onValueSelected = onMinuteSelected,
                    tagPrefix = "MinuteWheel",
                    displayText = { it.toString().padStart(2, '0') },
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
                DateControl(
                    label = "Once",
                    valueText = state.onceDateText,
                    tag = "OnceDateControl",
                    onClick = { activeDatePicker = DatePickerTarget.Once },
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
                                        locale,
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            if (state.repeatOption == EditorRepeatOption.INTERVAL) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateControl(
                        label = "Start",
                        valueText = state.intervalStartDateText,
                        tag = "IntervalStartDateControl",
                        onClick = { activeDatePicker = DatePickerTarget.IntervalStart },
                        modifier = Modifier.weight(1f),
                    )
                    DateControl(
                        label = "End",
                        valueText = state.intervalEndDateText,
                        tag = "IntervalEndDateControl",
                        onClick = { activeDatePicker = DatePickerTarget.IntervalEnd },
                        modifier = Modifier.weight(1f),
                    )
                }
                NumberWheelPicker(
                    label = "Weeks",
                    values = 1..104,
                    selectedValue = state.intervalWeeksText.toIntOrNull()?.coerceIn(1, 104) ?: 1,
                    onValueSelected = onIntervalWeeksSelected,
                    tagPrefix = "IntervalWeeksWheel",
                    displayText = { it.toString() },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in state.customWeekdays,
                            onClick = { onCustomWeekdayToggled(day) },
                            label = {
                                Text(
                                    text = day.getDisplayName(
                                        TextStyle.SHORT,
                                        locale,
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
            if (state.exactAlarmPermissionRequired) {
                Button(onClick = onOpenExactAlarmSettings) {
                    Text(text = "Open exact alarm settings")
                }
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

    activeDatePicker?.let { target ->
        val initialDate = when (target) {
            DatePickerTarget.Once -> state.onceDateText.toLocalDateOrToday()
            DatePickerTarget.IntervalStart -> state.intervalStartDateText.toLocalDateOrToday()
            DatePickerTarget.IntervalEnd -> state.intervalEndDateText.toLocalDateOrToday()
        }
        AlarmDatePickerDialog(
            target = target,
            initialDate = initialDate,
            onDismiss = { activeDatePicker = null },
            onDateSelected = { selectedDate ->
                when (target) {
                    DatePickerTarget.Once -> onOnceDateSelected(selectedDate)
                    DatePickerTarget.IntervalStart -> onIntervalStartDateSelected(selectedDate)
                    DatePickerTarget.IntervalEnd -> onIntervalEndDateSelected(selectedDate)
                }
                activeDatePicker = null
            },
        )
    }
}

private enum class DatePickerTarget(
    val dialogTag: String,
) {
    Once("DatePickerDialog-OnceDate"),
    IntervalStart("DatePickerDialog-IntervalStartDate"),
    IntervalEnd("DatePickerDialog-IntervalEndDate"),
}

@Composable
private fun DateControl(
    label: String,
    valueText: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AlarmDatePickerDialog(
    target: DatePickerTarget,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toUtcMillis(),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis
                        ?.toUtcLocalDate()
                        ?.let(onDateSelected)
                },
                modifier = Modifier.testTag("${target.dialogTag}-Confirm"),
            ) {
                Text(text = "OK")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("${target.dialogTag}-Cancel"),
            ) {
                Text(text = "Cancel")
            }
        },
        modifier = Modifier.testTag(target.dialogTag),
    ) {
        DatePicker(state = datePickerState)
    }
}

private fun String.toLocalDateOrToday(): LocalDate =
    runCatching { LocalDate.parse(this) }.getOrDefault(LocalDate.now())

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun NumberWheelPicker(
    label: String,
    values: IntRange,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit,
    tagPrefix: String,
    displayText: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val valueList = values.toList()
    val selectedIndex = valueList.indexOf(selectedValue).coerceAtLeast(0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0),
    )

    Column(
        modifier = modifier.width(104.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        LazyColumn(
            modifier = Modifier
                .height(144.dp)
                .fillMaxWidth()
                .testTag(tagPrefix)
                .semantics {
                    contentDescription = "$label picker selected ${displayText(selectedValue)}"
                },
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(valueList) { value ->
                val selected = value == selectedValue
                val itemTag = if (selected) {
                    "$tagPrefix-selected-${displayText(value)}"
                } else {
                    "$tagPrefix-${displayText(value)}"
                }
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .clickable { onValueSelected(value) }
                        .testTag(itemTag)
                        .semantics {
                            contentDescription = "$label ${displayText(value)}"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayText(value),
                        style = if (selected) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
