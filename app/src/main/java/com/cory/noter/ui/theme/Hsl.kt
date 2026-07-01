package com.cory.noter.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class Hsl(
    val h: Float,
    val s: Float,
    val l: Float,
)

internal fun Color.toHsl(): Hsl {
    val r = red
    val g = green
    val b = blue
    val max = max(max(r, g), b)
    val min = min(min(r, g), b)
    val delta = max - min

    val l = (max + min) / 2f

    if (delta == 0f) {
        return Hsl(h = 0f, s = 0f, l = l)
    }

    val s = if (l > 0.5f) {
        delta / (2f - max - min)
    } else {
        delta / (max + min)
    }

    val h = when (max) {
        r -> ((g - b) / delta + (if (g < b) 6f else 0f)) / 6f
        g -> ((b - r) / delta + 2f) / 6f
        else -> ((r - g) / delta + 4f) / 6f
    }

    return Hsl(h = h, s = s, l = l)
}

internal fun Hsl.toColor(): Color {
    if (s == 0f) {
        return Color(l, l, l)
    }

    val q = if (l < 0.5f) {
        l * (1f + s)
    } else {
        l + s - l * s
    }
    val p = 2f * l - q

    val r = hueToRgb(p, q, h + 1f / 3f)
    val g = hueToRgb(p, q, h)
    val b = hueToRgb(p, q, h - 1f / 3f)

    return Color(r, g, b)
}

private fun hueToRgb(p: Float, q: Float, t: Float): Float {
    var normalized = t
    if (normalized < 0f) normalized += 1f
    if (normalized > 1f) normalized -= 1f

    return when {
        normalized < 1f / 6f -> p + (q - p) * 6f * normalized
        normalized < 1f / 2f -> q
        normalized < 2f / 3f -> p + (q - p) * (2f / 3f - normalized) * 6f
        else -> p
    }
}

internal fun Float.clamp01(): Float = coerceIn(0f, 1f)

internal fun Color.roundChannels(): Color = Color(
    red = (red * 255).roundToInt() / 255f,
    green = (green * 255).roundToInt() / 255f,
    blue = (blue * 255).roundToInt() / 255f,
    alpha = (alpha * 255).roundToInt() / 255f,
)
