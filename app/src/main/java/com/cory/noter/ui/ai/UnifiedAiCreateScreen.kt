package com.cory.noter.ui.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cory.noter.R

object UnifiedAiCreateTestTags {
    const val Root = "UnifiedAiCreateRoot"
    const val VoiceModeAction = "UnifiedAiCreateVoiceModeAction"
    const val TextModeAction = "UnifiedAiCreateTextModeAction"
}

private enum class UnifiedAiCreateMode {
    Voice,
    Text,
}

@Composable
fun UnifiedAiCreateScreen(
    voiceContent: @Composable (onSwitchToText: () -> Unit) -> Unit,
    textContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(UnifiedAiCreateMode.Voice) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(UnifiedAiCreateTestTags.Root),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                ModeButton(
                    selected = mode == UnifiedAiCreateMode.Voice,
                    text = stringResource(R.string.ai_create_mode_voice),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(UnifiedAiCreateTestTags.VoiceModeAction),
                    onClick = { mode = UnifiedAiCreateMode.Voice },
                )
                ModeButton(
                    selected = mode == UnifiedAiCreateMode.Text,
                    text = stringResource(R.string.ai_create_mode_text),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(UnifiedAiCreateTestTags.TextModeAction),
                    onClick = { mode = UnifiedAiCreateMode.Text },
                )
            }

            when (mode) {
                UnifiedAiCreateMode.Voice -> voiceContent {
                    mode = UnifiedAiCreateMode.Text
                }

                UnifiedAiCreateMode.Text -> textContent()
            }
        }
    }
}

@Composable
private fun ModeButton(
    selected: Boolean,
    text: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            modifier = modifier,
            onClick = onClick,
        ) {
            Text(text = text)
        }
    } else {
        OutlinedButton(
            modifier = modifier,
            onClick = onClick,
        ) {
            Text(text = text)
        }
    }
}
