package com.cory.noter.ui.theme

import androidx.compose.ui.graphics.Color
import com.cory.noter.domain.settings.AppSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoterThemeTest {
    @Test
    fun `built in presets produce expected light primary colors`() {
        val expectedSeeds = mapOf(
            "calm_blue" to Color(0xFF4A6EA9),
            "fresh_green" to Color(0xFF3E6A4C),
            "soft_rose" to Color(0xFFB65B70),
            "neutral_gray" to Color(0xFF5F5F5F),
        )

        expectedSeeds.forEach { (presetId, expectedSeed) ->
            val scheme = noterLightColorScheme(settings(themePresetId = presetId))

            assertThat(scheme.primary).isEqualTo(expectedSeed)
        }
    }

    @Test
    fun `custom theme uses persisted seed as primary color`() {
        val scheme = noterLightColorScheme(
            settings(
                themePresetId = AppSettings.CustomThemePresetId,
                customThemeSeedColor = "#b65b70",
            ),
        )

        assertThat(scheme.primary).isEqualTo(Color(0xFFB65B70))
    }

    @Test
    fun `custom theme uses persisted seed in dark color scheme`() {
        val scheme = noterDarkColorScheme(
            settings(
                themePresetId = AppSettings.CustomThemePresetId,
                customThemeSeedColor = "#b65b70",
            ),
        )

        assertThat(scheme.primary).isEqualTo(Color(0xFFD6A3AF))
        assertThat(scheme.primaryContainer).isEqualTo(Color(0xFF6A3541))
    }

    @Test
    fun `unknown built in preset fails explicitly`() {
        val error = runCatching {
            noterLightColorScheme(settings(themePresetId = "electric_ultraviolet"))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("UNKNOWN_THEME_PRESET_ID")
    }

    @Test
    fun `malformed custom seed fails explicitly`() {
        val error = runCatching {
            noterLightColorScheme(
                settings(
                    themePresetId = AppSettings.CustomThemePresetId,
                    customThemeSeedColor = "not-a-color",
                ),
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("INVALID_THEME_SEED_COLOR")
    }

    @Test
    fun `custom theme without seed fails explicitly`() {
        val error = runCatching {
            noterLightColorScheme(
                settings(
                    themePresetId = AppSettings.CustomThemePresetId,
                    customThemeSeedColor = null,
                ),
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("INVALID_THEME_SEED_COLOR")
    }

    private fun settings(
        themePresetId: String,
        customThemeSeedColor: String? = null,
    ): AppSettings = AppSettings(
        openRouterApiKey = "",
        selectedModelId = "",
        selectedAsrModelId = "",
        defaultRingtoneUri = AppSettings.DefaultRingtoneUri,
        themePresetId = themePresetId,
        customThemeSeedColor = customThemeSeedColor,
    )
}
