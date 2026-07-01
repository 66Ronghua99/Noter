package com.cory.noter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.cory.noter.domain.settings.AppSettings

@Composable
fun NoterTheme(
    settings: AppSettings,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) {
            noterDarkColorScheme(settings)
        } else {
            noterLightColorScheme(settings)
        },
        content = content,
    )
}

fun noterLightColorScheme(settings: AppSettings): ColorScheme {
    val seed = settings.resolveThemeSeedColor()
    val palette = generateLightPalette(seed)
    return lightColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primaryContainer,
        onPrimaryContainer = palette.onPrimaryContainer,
        secondaryContainer = palette.secondaryContainer,
        onSecondaryContainer = palette.onSecondaryContainer,
        surface = Color(0xFFFAF9FD),
        surfaceContainerLow = Color(0xFFF3F3F9),
        surfaceContainer = Color(0xFFEEEDF4),
        surfaceContainerHigh = Color(0xFFE8E7EF),
        surfaceVariant = Color(0xFFDFE2EB),
        onSurface = Color(0xFF1A1C20),
        onSurfaceVariant = Color(0xFF43474E),
        outline = Color(0xFF74777F),
        outlineVariant = Color(0xFFC4C6D0),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    )
}

fun noterDarkColorScheme(settings: AppSettings): ColorScheme {
    val seed = settings.resolveThemeSeedColor()
    val palette = generateDarkPalette(seed)
    return darkColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primaryContainer,
        onPrimaryContainer = palette.onPrimaryContainer,
        secondaryContainer = palette.secondaryContainer,
        onSecondaryContainer = palette.onSecondaryContainer,
        surface = Color(0xFF111318),
        surfaceContainerLow = Color(0xFF191B20),
        surfaceContainer = Color(0xFF1D2026),
        surfaceContainerHigh = Color(0xFF282A31),
        surfaceVariant = Color(0xFF43474E),
        onSurface = Color(0xFFE3E2E8),
        onSurfaceVariant = Color(0xFFC4C6D0),
        outline = Color(0xFF8E9099),
        outlineVariant = Color(0xFF43474E),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    )
}

private data class NoterPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
)

private fun generateLightPalette(seed: Color): NoterPalette {
    val hsl = seed.toHsl()
    val isLightPrimary = hsl.l > 0.55f

    val primaryContainer = Hsl(
        h = hsl.h,
        s = (hsl.s * 0.42f).clampRange(0.1f, 0.45f),
        l = 0.91f,
    ).toColor().roundChannels()

    val onPrimaryContainer = if (isLightPrimary) {
        Hsl(
            h = hsl.h,
            s = (hsl.s * 0.55f).clampRange(0.15f, 0.55f),
            l = 0.16f,
        )
    } else {
        Hsl(
            h = hsl.h,
            s = (hsl.s * 0.35f).clampRange(0.1f, 0.4f),
            l = 0.94f,
        )
    }.toColor().roundChannels()

    val secondaryContainer = Hsl(
        h = hsl.h,
        s = (hsl.s * 0.30f).clampRange(0.08f, 0.35f),
        l = 0.93f,
    ).toColor().roundChannels()

    val onSecondaryContainer = if (isLightPrimary) {
        Hsl(
            h = hsl.h,
            s = (hsl.s * 0.45f).clampRange(0.12f, 0.5f),
            l = 0.18f,
        )
    } else {
        Hsl(
            h = hsl.h,
            s = (hsl.s * 0.28f).clampRange(0.08f, 0.35f),
            l = 0.92f,
        )
    }.toColor().roundChannels()

    return NoterPalette(
        primary = seed,
        onPrimary = seed.contentColor(),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
    )
}

private fun generateDarkPalette(seed: Color): NoterPalette {
    val primary = seed.mixWith(Color.White, 0.44f).roundChannels()

    return NoterPalette(
        primary = primary,
        onPrimary = Color(0xFF101010),
        primaryContainer = seed.mixWith(Color.Black, 0.42f).roundChannels(),
        onPrimaryContainer = Color(0xFFEFF2FF),
        secondaryContainer = seed.mixWith(Color.Black, 0.50f).roundChannels(),
        onSecondaryContainer = Color(0xFFE8ECF5),
    )
}

private val ThemeSeedColors: Map<String, Color> = mapOf(
    AppSettings.DefaultThemePresetId to Color(0xFF4A6EA9),
    "fresh_green" to Color(0xFF3E6A4C),
    "soft_rose" to Color(0xFFB65B70),
    "neutral_gray" to Color(0xFF5F5F5F),
)

private fun AppSettings.resolveThemeSeedColor(): Color = when (themePresetId) {
    AppSettings.CustomThemePresetId -> parseThemeSeedColor(
        requireNotNull(customThemeSeedColor) {
            "INVALID_THEME_SEED_COLOR: missing custom seed color"
        },
    )

    else -> requireNotNull(ThemeSeedColors[themePresetId]) {
        "UNKNOWN_THEME_PRESET_ID: $themePresetId"
    }
}

private fun parseThemeSeedColor(seedColor: String): Color {
    require(AppSettings.isValidThemeSeedColor(seedColor)) {
        "INVALID_THEME_SEED_COLOR: $seedColor"
    }
    val rgb = seedColor.removePrefix("#").toLong(radix = 16)
    return Color(0xFF000000 or rgb)
}

private fun Color.contentColor(): Color {
    val luminance = (0.299f * red) + (0.587f * green) + (0.114f * blue)
    return if (luminance > 0.55f) Color(0xFF1A1C20) else Color.White
}

private fun Color.mixWith(other: Color, otherWeight: Float): Color {
    val weight = otherWeight.coerceIn(0f, 1f)
    val selfWeight = 1f - weight
    return Color(
        red = (red * selfWeight) + (other.red * weight),
        green = (green * selfWeight) + (other.green * weight),
        blue = (blue * selfWeight) + (other.blue * weight),
        alpha = (alpha * selfWeight) + (other.alpha * weight),
    ).roundChannels()
}

private fun Float.clampRange(min: Float, max: Float): Float = coerceIn(min, max)
