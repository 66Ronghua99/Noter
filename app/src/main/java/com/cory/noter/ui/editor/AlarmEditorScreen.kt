package com.cory.noter.ui.editor

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cory.noter.R
import com.cory.noter.domain.alarm.AlarmValidation
import com.cory.noter.ui.text.asString
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import kotlinx.coroutines.launch
import kotlin.math.abs

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
        EditorRepeatOption.ONCE to stringResource(R.string.editor_repeat_once),
        EditorRepeatOption.DAILY to stringResource(R.string.editor_repeat_daily),
        EditorRepeatOption.WEEKDAYS to stringResource(R.string.editor_repeat_weekdays),
        EditorRepeatOption.CUSTOM to stringResource(R.string.editor_repeat_custom),
        EditorRepeatOption.INTERVAL to stringResource(R.string.editor_repeat_interval),
    )
    var activeDatePicker by remember { mutableStateOf<DatePickerTarget?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (state.isExisting) {
                                R.string.editor_title_edit
                            } else {
                                R.string.editor_title_new
                            },
                        ),
                    )
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
                label = { Text(text = stringResource(R.string.editor_title_label)) },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                NumberWheelPicker(
                    label = stringResource(R.string.editor_hours),
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
                    label = stringResource(R.string.editor_minutes),
                    values = 0..59,
                    selectedValue = state.minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0,
                    onValueSelected = onMinuteSelected,
                    tagPrefix = "MinuteWheel",
                    displayText = { it.toString().padStart(2, '0') },
                )
            }

            Text(
                text = stringResource(R.string.editor_repeat),
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
                    label = stringResource(R.string.editor_date_once),
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
                        label = stringResource(R.string.editor_date_start),
                        valueText = state.intervalStartDateText,
                        tag = "IntervalStartDateControl",
                        onClick = { activeDatePicker = DatePickerTarget.IntervalStart },
                        modifier = Modifier.weight(1f),
                    )
                    DateControl(
                        label = stringResource(R.string.editor_date_end),
                        valueText = state.intervalEndDateText,
                        tag = "IntervalEndDateControl",
                        onClick = { activeDatePicker = DatePickerTarget.IntervalEnd },
                        modifier = Modifier.weight(1f),
                    )
                }
                val weeksLabel = stringResource(R.string.editor_weeks)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("IntervalWeeksRow"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = weeksLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("IntervalWeeksLabel"),
                    )
                    NumberWheelPicker(
                        label = weeksLabel,
                        values = 1..104,
                        selectedValue = state.intervalWeeksText.toIntOrNull()?.coerceIn(1, 104) ?: 1,
                        onValueSelected = onIntervalWeeksSelected,
                        tagPrefix = "IntervalWeeksWheel",
                        displayText = { it.toString() },
                        width = 88.dp,
                        itemHeight = 40.dp,
                        visibleItemCount = 3,
                        showLabel = false,
                    )
                }
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
                text = stringResource(R.string.editor_ringtone),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = state.ringtoneUri,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onPickRingtone) {
                Text(text = stringResource(R.string.editor_choose_ringtone))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(R.string.common_enabled))
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onEnabledChanged,
                )
            }

            state.validationErrors.forEach { error ->
                Text(
                    text = stringResource(error.toMessageResId()),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.errorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage.asString(),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.exactAlarmPermissionRequired) {
                Button(onClick = onOpenExactAlarmSettings) {
                    Text(text = stringResource(R.string.editor_open_exact_alarm_settings))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSave) {
                    Text(text = stringResource(R.string.common_save))
                }
                if (state.isExisting) {
                    TextButton(onClick = onDelete) {
                        Text(text = stringResource(R.string.common_delete))
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
                Text(text = stringResource(R.string.editor_date_picker_ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("${target.dialogTag}-Cancel"),
            ) {
                Text(text = stringResource(R.string.editor_date_picker_cancel))
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
    width: Dp = 104.dp,
    itemHeight: Dp = 48.dp,
    visibleItemCount: Int = 3,
    showLabel: Boolean = true,
) {
    val viewportHeight = itemHeight * visibleItemCount
    val edgePadding = itemHeight * ((visibleItemCount - 1) / 2f)
    val valueList = values.toList()
    val selectedIndex = valueList.indexOf(selectedValue).coerceAtLeast(0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex,
    )
    val flingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center,
    )
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val currentSelectedValue by rememberUpdatedState(selectedValue)
    val currentOnValueSelected by rememberUpdatedState(onValueSelected)
    val pickerContentDescription = stringResource(
        R.string.picker_selected_content_description,
        label,
        displayText(selectedValue),
    )
    val centeredIndex by remember {
        derivedStateOf {
            listState.centeredIndex()
        }
    }

    LaunchedEffect(selectedValue, selectedIndex) {
        if (!listState.isScrollInProgress) {
            listState.scrollToItem(index = selectedIndex)
        }
    }

    LaunchedEffect(listState, valueList) {
        snapshotFlow {
            listState.isScrollInProgress to listState.centeredValue(valueList)
        }.collect { (isScrolling, centeredValue) ->
            if (!isScrolling) {
                if (centeredValue != null && centeredValue != currentSelectedValue) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnValueSelected(centeredValue)
                }
            }
        }
    }

    Column(
        modifier = modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(
            modifier = Modifier
                .height(viewportHeight)
                .fillMaxWidth()
                .semantics {
                    contentDescription = pickerContentDescription
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .height(itemHeight)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 3.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.48f),
                        shape = MaterialTheme.shapes.small,
                    )
                    .testTag(wheelSelectionFrameTag(tagPrefix)),
            )
            LazyColumn(
                modifier = Modifier
                    .height(viewportHeight)
                    .fillMaxWidth()
                    .testTag(tagPrefix),
                state = listState,
                contentPadding = PaddingValues(vertical = edgePadding),
                flingBehavior = flingBehavior,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(valueList) { index, value ->
                    val selected = value == selectedValue
                    val visualDistance = centeredIndex?.let { abs(index - it) }
                        ?: abs(index - selectedIndex)
                    val valueContentDescription = stringResource(
                        R.string.picker_value_content_description,
                        label,
                        displayText(value),
                    )
                    val itemTag = if (selected) {
                        "$tagPrefix-selected-${displayText(value)}"
                    } else {
                        "$tagPrefix-${displayText(value)}"
                    }
                    Box(
                        modifier = Modifier
                            .height(itemHeight)
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 3.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                onValueSelected(value)
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index = index)
                                }
                            }
                            .testTag(itemTag)
                            .semantics {
                                contentDescription = valueContentDescription
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = displayText(value),
                            style = if (visualDistance == 0) {
                                MaterialTheme.typography.headlineSmall
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            color = if (visualDistance == 0) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (visualDistance == 0) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.alpha(
                                when (visualDistance) {
                                    0 -> 1f
                                    1 -> 0.68f
                                    else -> 0.38f
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

internal fun wheelSelectionFrameTag(tagPrefix: String): String = "${tagPrefix}SelectionFrame"

private fun LazyListState.centeredIndex(): Int? {
    val layoutInfo = layoutInfo
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    return layoutInfo.visibleItemsInfo
        .minByOrNull { item ->
            abs((item.offset + item.size / 2) - viewportCenter)
        }
        ?.index
}

private fun LazyListState.centeredValue(values: List<Int>): Int? {
    return centeredIndex()
        ?.let(values::getOrNull)
}

@StringRes
private fun AlarmValidation.Error.toMessageResId(): Int = when (this) {
    AlarmValidation.Error.BLANK_TITLE -> R.string.editor_validation_blank_title
    AlarmValidation.Error.INVALID_HOUR -> R.string.editor_validation_invalid_hour
    AlarmValidation.Error.INVALID_MINUTE -> R.string.editor_validation_invalid_minute
    AlarmValidation.Error.EMPTY_CUSTOM_WEEKDAYS -> R.string.editor_validation_empty_custom_weekdays
    AlarmValidation.Error.EMPTY_INTERVAL_WEEKDAYS -> R.string.editor_validation_empty_interval_weekdays
    AlarmValidation.Error.INVALID_INTERVAL_WEEKS -> R.string.editor_validation_invalid_interval_weeks
    AlarmValidation.Error.INVALID_INTERVAL_RANGE -> R.string.editor_validation_invalid_interval_range
    AlarmValidation.Error.EXPIRED_ONE_TIME_ALARM -> R.string.editor_validation_expired_one_time_alarm
    AlarmValidation.Error.EXPIRED_INTERVAL_ALARM -> R.string.editor_validation_expired_interval_alarm
}
