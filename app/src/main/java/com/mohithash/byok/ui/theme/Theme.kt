@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mohithash.byok.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    )
}

/** Deep shade of the primary seed, used as the far end of hero gradients. */
val LocalHeroDeep = compositionLocalOf { Color(0xFF102030) }

/* ── HSL helpers to derive a full scheme from three seed colours ── */
private fun Color.hsl(): FloatArray {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val l = (max + min) / 2
    if (max == min) return floatArrayOf(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2 - max - min) else d / (max + min)
    val h = when (max) { r -> ((g - b) / d + (if (g < b) 6 else 0)) / 6; g -> ((b - r) / d + 2) / 6; else -> ((r - g) / d + 4) / 6 }
    return floatArrayOf(h, s, l)
}
private fun hslColor(h: Float, s: Float, l: Float): Color {
    fun hue(p: Float, q: Float, tt: Float): Float { var t = tt; if (t < 0) t += 1; if (t > 1) t -= 1
        return when { t < 1f / 6 -> p + (q - p) * 6 * t; t < 0.5f -> q; t < 2f / 3 -> p + (q - p) * (2f / 3 - t) * 6; else -> p } }
    if (s == 0f) return Color(l, l, l)
    val q = if (l < 0.5f) l * (1 + s) else l + s - l * s; val p = 2 * l - q
    return Color(hue(p, q, h + 1f / 3), hue(p, q, h), hue(p, q, h - 1f / 3))
}
fun Color.tone(l: Float, s: Float? = null): Color { val (h, ss, _) = hsl(); return hslColor(h, s ?: ss, l) }

fun schemeFor(primary: Color, secondary: Color, tertiary: Color, dark: Boolean): ColorScheme {
    fun role(c: Color) = if (!dark) listOf(c.tone(.32f), Color.White, c.tone(.88f), c.tone(.10f)) else listOf(c.tone(.75f), c.tone(.15f), c.tone(.28f), c.tone(.90f))
    val p = role(primary); val s = role(secondary); val t = role(tertiary)
    fun n(l: Float, sat: Float) = primary.tone(l, sat)
    return if (!dark) lightColorScheme(
        primary = p[0], onPrimary = p[1], primaryContainer = p[2], onPrimaryContainer = p[3], inversePrimary = primary.tone(.75f),
        secondary = s[0], onSecondary = s[1], secondaryContainer = s[2], onSecondaryContainer = s[3],
        tertiary = t[0], onTertiary = t[1], tertiaryContainer = t[2], onTertiaryContainer = t[3],
        background = n(.975f, .35f), onBackground = n(.11f, .15f), surface = n(.975f, .35f), onSurface = n(.11f, .15f),
        surfaceVariant = n(.90f, .18f), onSurfaceVariant = n(.28f, .12f), surfaceContainerLowest = Color.White, surfaceContainerLow = n(.955f, .3f),
        surfaceContainer = n(.93f, .28f), surfaceContainerHigh = n(.905f, .25f), surfaceContainerHighest = n(.88f, .22f), outline = n(.45f, .10f), outlineVariant = n(.78f, .15f),
        error = Color(0xFFBA1A1A), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    ) else darkColorScheme(
        primary = p[0], onPrimary = p[1], primaryContainer = p[2], onPrimaryContainer = p[3], inversePrimary = primary.tone(.32f),
        secondary = s[0], onSecondary = s[1], secondaryContainer = s[2], onSecondaryContainer = s[3],
        tertiary = t[0], onTertiary = t[1], tertiaryContainer = t[2], onTertiaryContainer = t[3],
        background = n(.075f, .25f), onBackground = n(.90f, .10f), surface = n(.075f, .25f), onSurface = n(.90f, .10f),
        surfaceVariant = n(.28f, .12f), onSurfaceVariant = n(.78f, .10f), surfaceContainerLowest = n(.05f, .25f), surfaceContainerLow = n(.10f, .22f),
        surfaceContainer = n(.125f, .20f), surfaceContainerHigh = n(.16f, .18f), surfaceContainerHighest = n(.20f, .16f), outline = n(.55f, .10f), outlineVariant = n(.28f, .12f),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    )
}

@Composable
fun AppTheme(primary: Color, secondary: Color, tertiary: Color, darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalHeroDeep provides primary.tone(.18f)) {
        MaterialExpressiveTheme(colorScheme = schemeFor(primary, secondary, tertiary, darkTheme), motionScheme = MotionScheme.expressive(), typography = AppTypography, content = content)
    }
}
