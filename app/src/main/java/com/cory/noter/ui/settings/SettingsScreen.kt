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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cory.noter.R
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
    const val CustomThemeSeedSaveAction = "SettingsCustomThemeSeedSaveAction"
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
        LazyColumn(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.directoryRows, key = { it.id }) { row ->
                SettingsDirectoryRow(
                    row = row,
                    modifier = Modifier.testTag(row.tag),
                    onClick = when (row.id) {
                        "appearance" -> onOpenAppearance
                        "ai_voice" -> onOpenAiVoice
                        "sound" -> onOpenSound
                        "permissions" -> onOpenPermissions
                        else -> error("Unknown settings directory row: ${row.id}")
                    },
                )
            }
            item {
                SettingsError(state.errorMessage)
            }
        }
    }
}

@Composable
fun AppearanceSettingsScreen(
    state: SettingsUiState,
    onThemePresetSelected: (String) -> Unit,
    onCustomThemeSeedColorChanged: (String) -> Unit,
    onSaveCustomThemeSeedColor: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_appearance_title),
        onBack = onBack,
        modifier = modifier.testTag(SettingsTestTags.AppearanceDetail),
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionTitle(text = stringResource(R.string.settings_theme_presets))
            }
            items(state.themePresetOptions, key = { it }) { presetId ->
                SelectableRow(
                    text = presetLabel(presetId),
                    selected = state.themePresetId == presetId,
                    modifier = Modifier.testTag(SettingsTestTags.ThemePresetAction(presetId)),
                    onClick = { onThemePresetSelected(presetId) },
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(text = stringResource(R.string.settings_custom_theme_seed))
                    OutlinedTextField(
                        value = state.customThemeSeedColorInput,
                        onValueChange = onCustomThemeSeedColorChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SettingsTestTags.CustomThemeSeedInput),
                        label = { Text(text = stringResource(R.string.settings_custom_theme_seed_label)) },
                    )
                    Button(
                        modifier = Modifier.testTag(SettingsTestTags.CustomThemeSeedSaveAction),
                        onClick = onSaveCustomThemeSeedColor,
                    ) {
                        Text(text = stringResource(R.string.settings_save_custom_theme_seed))
                    }
                    SettingsError(state.errorMessage)
                }
            }
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
        LazyColumn(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(text = stringResource(R.string.settings_openrouter_api_key))
                    OutlinedTextField(
                        value = state.openRouterApiKey,
                        onValueChange = onApiKeyChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SettingsTestTags.ApiKeyInput),
                        label = { Text(text = stringResource(R.string.settings_api_key_label)) },
                    )
                    Button(
                        modifier = Modifier.testTag(SettingsTestTags.SaveApiKeyAction),
                        onClick = onSaveApiKey,
                    ) {
                        Text(text = stringResource(R.string.settings_save_api_key))
                    }
                }
            }
            item {
                SectionTitle(text = stringResource(R.string.settings_model))
            }
            items(state.modelOptions, key = { it }) { modelId ->
                SelectableRow(
                    text = modelId,
                    selected = state.selectedModelId == modelId,
                    modifier = Modifier.testTag(SettingsTestTags.ModelAction(modelId)),
                    onClick = { onModelSelected(modelId) },
                )
            }
            item {
                SectionTitle(text = stringResource(R.string.settings_asr_model))
            }
            items(state.asrModelOptions, key = { it }) { modelId ->
                SelectableRow(
                    text = modelId,
                    selected = state.selectedAsrModelId == modelId,
                    modifier = Modifier.testTag(SettingsTestTags.AsrModelAction(modelId)),
                    onClick = { onAsrModelSelected(modelId) },
                )
            }
            item {
                SettingsError(state.errorMessage)
            }
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
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(text = stringResource(R.string.settings_default_ringtone))
            Text(
                text = state.defaultRingtoneUri,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                modifier = Modifier.testTag(SettingsTestTags.DefaultRingtoneAction),
                onClick = onPickDefaultRingtone,
            ) {
                Text(text = stringResource(R.string.settings_choose_default_ringtone))
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
        LazyColumn(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.permissionRows, key = { it.id }) { row ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(row.titleResId),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(
                                if (row.granted) {
                                    R.string.common_granted
                                } else {
                                    R.string.common_needs_attention
                                },
                            ),
                            color = if (row.granted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                        Text(
                            text = stringResource(row.summaryResId),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!row.granted && row.actionLabelResId != null) {
                            Button(
                                modifier = Modifier.testTag(SettingsTestTags.PermissionAction(row.id)),
                                onClick = { onPermissionAction(row.id) },
                            ) {
                                Text(text = stringResource(row.actionLabelResId))
                            }
                        }
                    }
                }
            }
            item {
                SettingsError(state.errorMessage)
            }
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
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        content(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        )
    }
}

@Composable
private fun SettingsDirectoryRow(
    row: SettingsDirectoryRowUiModel,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(row.titleResId),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = row.summary.asString(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SelectableRow(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Text(text = text)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingsError(errorMessage: com.cory.noter.ui.text.UiText?) {
    errorMessage?.let {
        Text(
            text = it.asString(),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun presetLabel(presetId: String): String = when (presetId) {
    "calm_blue" -> stringResource(R.string.settings_theme_preset_calm_blue)
    "fresh_green" -> stringResource(R.string.settings_theme_preset_fresh_green)
    "soft_rose" -> stringResource(R.string.settings_theme_preset_soft_rose)
    "neutral_gray" -> stringResource(R.string.settings_theme_preset_neutral_gray)
    else -> presetId
}

private val SettingsDirectoryRowUiModel.tag: String
    get() = when (id) {
        "appearance" -> SettingsTestTags.AppearanceRow
        "ai_voice" -> SettingsTestTags.AiVoiceRow
        "sound" -> SettingsTestTags.SoundRow
        "permissions" -> SettingsTestTags.PermissionsRow
        else -> "SettingsDirectoryRow:$id"
    }
