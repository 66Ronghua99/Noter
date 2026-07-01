package com.cory.noter.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cory.noter.R
import com.cory.noter.domain.settings.AppSettings
import com.cory.noter.ui.text.UiText
import com.cory.noter.ui.text.asString

object SettingsTestTags {
    const val Home = "SettingsHome"
    const val AppearanceRow = "SettingsAppearanceRow"
    const val AiVoiceRow = "SettingsAiVoiceRow"
    const val SoundRow = "SettingsSoundRow"
    const val PermissionsRow = "SettingsPermissionsRow"
    const val AppearanceDetail = "SettingsAppearanceDetail"
    const val AiVoiceDetail = "SettingsAiVoiceDetail"
    const val SoundDetail = "SettingsSoundDetail"
    const val PermissionsDetail = "SettingsPermissionsDetail"
    const val ApiKeyInput = "SettingsApiKeyInput"
    const val SaveApiKeyAction = "SettingsSaveApiKeyAction"
    const val CustomThemeSeedInput = "SettingsCustomThemeSeedInput"
    const val DefaultRingtoneAction = "SettingsDefaultRingtoneAction"

    fun ThemePresetAction(presetId: String): String = "SettingsThemePresetAction:$presetId"
    fun ModelAction(modelId: String): String = "SettingsModelAction:$modelId"
    fun AsrModelAction(modelId: String): String = "SettingsAsrModelAction:$modelId"
    fun PermissionAction(permissionId: String): String = "SettingsPermissionAction:$permissionId"
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onOpenAppearance: () -> Unit,
    onOpenAiVoice: () -> Unit,
    onOpenSound: () -> Unit,
    onOpenPermissions: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = modifier.testTag(SettingsTestTags.Home),
    ) { contentModifier ->
        Column(
            modifier = contentModifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PageHeader(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_page_subtitle),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.directoryRows.forEach { row ->
                    val onClick = when (row.id) {
                        "appearance" -> onOpenAppearance
                        "ai_voice" -> onOpenAiVoice
                        "sound" -> onOpenSound
                        "permissions" -> onOpenPermissions
                        else -> error("Unknown settings directory row: ${row.id}")
                    }
                    SettingsDirectoryRow(
                        row = row,
                        modifier = Modifier.testTag(row.tag),
                        onClick = onClick,
                    )
                }
            }

            SettingsError(state.errorMessage)
        }
    }
}

@Composable
fun AppearanceSettingsScreen(
    state: SettingsUiState,
    onThemePresetSelected: (String) -> Unit,
    onCustomThemeSeedColorChanged: (String) -> Unit,
    onCustomThemeSeedColorCommitted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_appearance_title),
        onBack = onBack,
        modifier = modifier.testTag(SettingsTestTags.AppearanceDetail),
    ) { contentModifier ->
        Column(
            modifier = contentModifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PageHeader(
                title = stringResource(R.string.settings_appearance_title),
                subtitle = stringResource(R.string.settings_appearance_subtitle),
            )

            SectionLabel(text = stringResource(R.string.settings_theme_presets))
            PresetGrid(
                selectedPresetId = state.themePresetId,
                onPresetSelected = onThemePresetSelected,
            )

            SectionLabel(text = stringResource(R.string.settings_custom_theme_seed))
            CustomColorRow(
                seedColor = state.customThemeSeedColorInput,
                onSeedColorChanged = onCustomThemeSeedColorChanged,
                onSeedColorCommitted = onCustomThemeSeedColorCommitted,
            )

            SectionLabel(text = stringResource(R.string.settings_theme_preview))
            ThemePreviewCard(state = state)

            SettingsError(state.errorMessage)
        }
    }
}

@Composable
fun AiVoiceSettingsScreen(
    state: SettingsUiState,
    onApiKeyChanged: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onModelSelected: (String) -> Unit,
    onAsrModelSelected: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_ai_voice_title),
        onBack = onBack,
        modifier = modifier.testTag(SettingsTestTags.AiVoiceDetail),
    ) { contentModifier ->
        Column(
            modifier = contentModifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PageHeader(
                title = stringResource(R.string.settings_ai_voice_title),
                subtitle = stringResource(R.string.settings_ai_voice_subtitle),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FormField(
                        label = stringResource(R.string.settings_openrouter_api_key),
                        hint = stringResource(R.string.settings_api_key_hint),
                    ) {
                        OutlinedTextField(
                            value = state.openRouterApiKey,
                            onValueChange = onApiKeyChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(SettingsTestTags.ApiKeyInput),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    FormField(
                        label = stringResource(R.string.settings_model),
                        hint = null,
                    ) {
                        ModelDropdown(
                            options = state.modelOptions,
                            selectedOption = state.selectedModelId,
                            onOptionSelected = onModelSelected,
                            testTagBuilder = SettingsTestTags::ModelAction,
                        )
                    }

                    FormField(
                        label = stringResource(R.string.settings_asr_model),
                        hint = null,
                    ) {
                        ModelDropdown(
                            options = state.asrModelOptions,
                            selectedOption = state.selectedAsrModelId,
                            onOptionSelected = onAsrModelSelected,
                            testTagBuilder = SettingsTestTags::AsrModelAction,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text(text = stringResource(R.string.common_cancel))
                }
                Button(
                    modifier = Modifier.testTag(SettingsTestTags.SaveApiKeyAction),
                    onClick = onSaveApiKey,
                ) {
                    Text(text = stringResource(R.string.settings_save_api_key))
                }
            }

            SettingsError(state.errorMessage)
        }
    }
}

@Composable
fun SoundSettingsScreen(
    state: SettingsUiState,
    onPickDefaultRingtone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_sound_title),
        onBack = onBack,
        modifier = modifier.testTag(SettingsTestTags.SoundDetail),
    ) { contentModifier ->
        Column(
            modifier = contentModifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PageHeader(
                title = stringResource(R.string.settings_sound_title),
                subtitle = stringResource(R.string.settings_sound_subtitle),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FormField(
                        label = stringResource(R.string.settings_default_ringtone),
                        hint = stringResource(R.string.settings_default_ringtone_hint),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Text(
                                text = state.defaultRingtoneUri,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Button(
                        modifier = Modifier.testTag(SettingsTestTags.DefaultRingtoneAction),
                        onClick = onPickDefaultRingtone,
                    ) {
                        Text(text = stringResource(R.string.settings_choose_default_ringtone))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text(text = stringResource(R.string.common_cancel))
                }
                Button(onClick = onBack) {
                    Text(text = stringResource(R.string.common_save))
                }
            }

            SettingsError(state.errorMessage)
        }
    }
}

@Composable
fun PermissionsSettingsScreen(
    state: SettingsUiState,
    onPermissionAction: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_permissions_title),
        onBack = onBack,
        modifier = modifier.testTag(SettingsTestTags.PermissionsDetail),
    ) { contentModifier ->
        Column(
            modifier = contentModifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PageHeader(
                title = stringResource(R.string.settings_permissions_title),
                subtitle = stringResource(R.string.settings_permissions_subtitle),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.permissionRows.forEach { row ->
                    PermissionRow(
                        row = row,
                        modifier = Modifier.testTag(SettingsTestTags.PermissionAction(row.id)),
                        onAction = { onPermissionAction(row.id) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.settings_permissions_helper),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsError(state.errorMessage)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        content(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PageHeader(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing,
    )
}

@Composable
private fun SettingsDirectoryRow(
    row: SettingsDirectoryRowUiModel,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val icon = when (row.id) {
        "appearance" -> Icons.Default.Palette
        "ai_voice" -> Icons.Default.Psychology
        "sound" -> Icons.AutoMirrored.Filled.VolumeUp
        "permissions" -> Icons.Default.Security
        else -> Icons.Default.Settings
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(row.titleResId),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = row.summary.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class PresetOption(
    val id: String,
    @param:StringRes val nameResId: Int,
    val seedColor: Color,
)

private val presetOptions: List<PresetOption> = listOf(
    PresetOption("calm_blue", R.string.settings_theme_preset_calm_blue, Color(0xFF4A6EA9)),
    PresetOption("fresh_green", R.string.settings_theme_preset_fresh_green, Color(0xFF3E6A4C)),
    PresetOption("soft_rose", R.string.settings_theme_preset_soft_rose, Color(0xFFB65B70)),
    PresetOption("neutral_gray", R.string.settings_theme_preset_neutral_gray, Color(0xFF5F5F5F)),
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PresetGrid(
    selectedPresetId: String,
    onPresetSelected: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = 2,
    ) {
        presetOptions.forEach { preset ->
            val selected = selectedPresetId == preset.id
            Card(
                modifier = Modifier
                    .weight(1f)
                    .testTag(SettingsTestTags.ThemePresetAction(preset.id)),
                onClick = { onPresetSelected(preset.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(preset.seedColor)
                            .then(
                                if (selected) {
                                    Modifier.padding(2.dp)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White,
                            )
                        }
                    }
                    Text(
                        text = stringResource(preset.nameResId),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomColorRow(
    seedColor: String,
    onSeedColorChanged: (String) -> Unit,
    onSeedColorCommitted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsedColor = remember(seedColor) {
        parsePreviewColor(seedColor)
    }
    var wasFocused by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = parsedColor,
                border = BorderStroke(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.surface,
                ),
                shadowElevation = 2.dp,
            ) {}

            OutlinedTextField(
                value = seedColor,
                onValueChange = onSeedColorChanged,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (wasFocused && !focusState.isFocused) {
                            onSeedColorCommitted()
                        }
                        wasFocused = focusState.isFocused
                    }
                    .testTag(SettingsTestTags.CustomThemeSeedInput),
                label = { Text(text = stringResource(R.string.settings_custom_theme_seed_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                isError = parsedColor == Color.Unspecified && seedColor.isNotBlank(),
            )
        }
    }
}

private fun parsePreviewColor(seedColor: String): Color {
    val normalizedSeedColor = AppSettings.normalizeThemeSeedColorInput(seedColor) ?: return Color.Unspecified
    val rgb = normalizedSeedColor.removePrefix("#").toLong(16)
    return Color(0xFF000000 or rgb)
}

@Composable
private fun ThemePreviewCard(state: SettingsUiState) {
    val (title, color) = when (state.themePresetId) {
        AppSettings.CustomThemePresetId -> {
            val seed = state.customThemeSeedColor ?: state.customThemeSeedColorInput
            stringResource(R.string.settings_theme_preset_custom) to parsePreviewColor(seed)
        }

        "calm_blue" -> stringResource(R.string.settings_theme_preset_calm_blue) to Color(0xFF4A6EA9)
        "fresh_green" -> stringResource(R.string.settings_theme_preset_fresh_green) to Color(0xFF3E6A4C)
        "soft_rose" -> stringResource(R.string.settings_theme_preset_soft_rose) to Color(0xFFB65B70)
        "neutral_gray" -> stringResource(R.string.settings_theme_preset_neutral_gray) to Color(0xFF5F5F5F)
        else -> stringResource(R.string.settings_theme_preset_calm_blue) to Color(0xFF4A6EA9)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color,
                shadowElevation = 2.dp,
            ) {}

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings_theme_preview_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    testTagBuilder: (String) -> String,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .testTag(testTagBuilder(selectedOption)),
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = MaterialTheme.shapes.medium,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                    modifier = Modifier.testTag(testTagBuilder(option)),
                )
            }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    hint: String?,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        content()
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    row: PermissionGuidanceUiModel,
    modifier: Modifier,
    onAction: () -> Unit,
) {
    val icon = when (row.id) {
        "notifications" -> Icons.Default.Notifications
        "exact_alarms" -> Icons.Default.Schedule
        "battery_optimization" -> Icons.Outlined.BatterySaver
        else -> Icons.Default.Security
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = if (row.granted) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (row.granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(row.titleResId),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(
                        if (row.granted) {
                            R.string.common_granted
                        } else {
                            R.string.common_needs_attention
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (row.granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            if (!row.granted && row.actionLabelResId != null) {
                Button(onClick = onAction) {
                    Text(text = stringResource(row.actionLabelResId))
                }
            } else {
                TextButton(
                    onClick = {},
                    enabled = false,
                ) {
                    Text(text = stringResource(R.string.common_granted))
                }
            }
        }
    }
}

@Composable
private fun SettingsError(errorMessage: UiText?) {
    errorMessage?.let {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Text(
                text = it.asString(),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private val SettingsDirectoryRowUiModel.tag: String
    get() = when (id) {
        "appearance" -> SettingsTestTags.AppearanceRow
        "ai_voice" -> SettingsTestTags.AiVoiceRow
        "sound" -> SettingsTestTags.SoundRow
        "permissions" -> SettingsTestTags.PermissionsRow
        else -> "SettingsDirectoryRow:$id"
    }
