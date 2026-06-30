package com.cory.noter.ui.theme

import androidx.compose.ui.graphics.Color
import com.cory.noter.domain.settings.AppSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoterThemeTest {
    @Test
    fun `built in presets produce distinct primary colors`() {
        val calmBlue = noterLightColorScheme(
            AppSettings(
                openRouterApiKey = "",
                selectedModelId = "",
                selectedAsrModelId = "",
                defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
                themePresetId = "calm_blue",
            ),
        )
        val freshGreen = noterLightColorScheme(
            AppSettings(
                openRouterApiKey = "",
                selectedModelId = "",
                selectedAsrModelId = "",
                defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
                themePresetId = "fresh_green",
            ),
        )

        assertThat(freshGreen.primary).isNotEqualTo(calmBlue.primary)
    }

    @Test
    fun `custom theme uses persisted seed as primary color`() {
        val scheme = noterLightColorScheme(
            AppSettings(
                openRouterApiKey = "",
                selectedModelId = "",
                selectedAsrModelId = "",
                defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
                themePresetId = AppSettings.CustomThemePresetId,
                customThemeSeedColor = "#b65b70",
            ),
        )

        assertThat(scheme.primary).isEqualTo(Color(0xFFB65B70))
    }

    @Test
    fun `custom theme without seed fails explicitly`() {
        val error = runCatching {
            noterLightColorScheme(
                AppSettings(
                    openRouterApiKey = "",
                    selectedModelId = "",
                    selectedAsrModelId = "",
                    defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
                    themePresetId = AppSettings.CustomThemePresetId,
                    customThemeSeedColor = null,
                ),
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("INVALID_THEME_SEED_COLOR")
    }
}
