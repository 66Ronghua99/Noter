package com.cory.noter.ui.alarm_list

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cory.noter.R
import com.cory.noter.domain.alarm.RepeatRule
import com.cory.noter.ui.text.asString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object AlarmListTestTags {
    const val SettingsAction = "AlarmListSettingsAction"
    const val CreateTabAction = "AlarmListCreateTabAction"
    const val ListTabAction = "AlarmListListTabAction"
}

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
    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                        modifier = Modifier.testTag(AlarmListTestTags.SettingsAction),
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
        bottomBar = {
            BottomCreateListBar(
                selectedCreate = false,
                onCreateClick = onOpenAiCreate,
                onListClick = {},
            )
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
                    text = stringResource(R.string.alarm_list_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.alarm_list_empty_body),
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
                            text = errorMessage.asString(),
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
            modifier = Modifier.testTag(AlarmListTestTags.CreateTabAction),
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
            modifier = Modifier.testTag(AlarmListTestTags.ListTabAction),
        )
    }
}

@Composable
private fun AlarmRow(
    alarm: AlarmListItemUiModel,
    onAlarmEnabledChanged: (Long, Boolean) -> Unit,
    onEditAlarm: (Long) -> Unit,
    onDeleteAlarm: (Long) -> Unit,
) {
    val locale = currentConfigurationLocale()
    val nextTriggerText = formatNextTrigger(alarm.nextTriggerAtMillis, locale)
    val repeatLabel = alarm.repeatRule.toLabel(locale)

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
                        text = stringResource(R.string.alarm_list_next_format, nextTriggerText),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.alarm_list_repeat_format, repeatLabel),
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
                    Text(text = stringResource(R.string.alarm_list_edit))
                }
                TextButton(onClick = { onDeleteAlarm(alarm.id) }) {
                    Text(text = stringResource(R.string.common_delete))
                }
            }
        }
    }
}

@Composable
private fun currentConfigurationLocale(): Locale {
    val configuration = LocalConfiguration.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        configuration.locale
    }
}

@Composable
private fun formatNextTrigger(
    nextTriggerAtMillis: Long?,
    locale: Locale,
): String {
    if (nextTriggerAtMillis == null) {
        return stringResource(R.string.alarm_list_not_scheduled)
    }

    val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", locale)
    return formatter.format(
        Instant.ofEpochMilli(nextTriggerAtMillis).atZone(ZoneId.systemDefault()),
    )
}

@Composable
private fun RepeatRule.toLabel(locale: Locale): String = when (this) {
    is RepeatRule.Once -> stringResource(R.string.alarm_list_repeat_once)
    RepeatRule.Daily -> stringResource(R.string.alarm_list_repeat_daily)
    RepeatRule.Weekdays -> stringResource(R.string.alarm_list_repeat_weekdays)
    is RepeatRule.CustomWeekdays -> days.toWeekdayLabel(locale)
    is RepeatRule.WeeklyInterval -> stringResource(
        R.string.alarm_list_repeat_weekly_interval_format,
        intervalWeeks,
        days.toWeekdayLabel(locale),
    )
}

private fun Set<java.time.DayOfWeek>.toWeekdayLabel(locale: Locale): String =
    sortedBy { it.value }
        .joinToString(", ") { day ->
            day.getDisplayName(TextStyle.SHORT, locale)
        }
