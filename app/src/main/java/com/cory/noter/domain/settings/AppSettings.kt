package com.cory.noter.domain.settings

data class AppSettings(
    val openRouterApiKey: String,
    val selectedModelId: String,
    val selectedAsrModelId: String,
    val defaultRingtoneUri: String,
    val themePresetId: String = DefaultThemePresetId,
    val customThemeSeedColor: String? = null,
) {
    companion object {
        const val DefaultRingtoneUri: String = "content://settings/system/alarm_alert"
        const val DefaultThemePresetId: String = "calm_blue"
        const val CustomThemePresetId: String = "custom"

        val BuiltInThemePresetIds: Set<String> = setOf(
            DefaultThemePresetId,
            "fresh_green",
            "soft_rose",
            "neutral_gray",
        )

        fun isValidThemeSeedColor(seedColor: String): Boolean =
            THEME_SEED_COLOR_PATTERN.matches(seedColor)

        fun normalizeThemeSeedColorInput(seedColor: String): String? {
            val trimmed = seedColor.trim()
            val normalized = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
            return normalized.lowercase().takeIf(::isValidThemeSeedColor)
        }

        private val THEME_SEED_COLOR_PATTERN = Regex("^#[0-9a-fA-F]{6}$")
    }
}
