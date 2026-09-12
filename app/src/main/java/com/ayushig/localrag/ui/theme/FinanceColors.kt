package com.ayushig.localrag.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Gain and loss colours, defined once.
 *
 * These sit outside the Material [androidx.compose.material3.ColorScheme] on purpose: the app
 * enables dynamic colour, and a wallpaper-derived palette must never be allowed to turn a loss
 * green. Colour is a reinforcement only — every value also carries an explicit + or -.
 */
@Immutable
data class FinanceColors(
    val positive: Color,
    val negative: Color,
    val neutral: Color,
    val positiveContainer: Color,
    val negativeContainer: Color,
) {
    /** Picks the colour for a signed amount. Exactly zero is neutral, not a gain. */
    fun forAmount(amount: Double): Color = when {
        amount > 0.0 -> positive
        amount < 0.0 -> negative
        else -> neutral
    }
}

val LightFinanceColors = FinanceColors(
    positive = Color(0xFF10743C),
    negative = Color(0xFFB3261E),
    neutral = Color(0xFF5C5F62),
    positiveContainer = Color(0xFFD3EFDD),
    negativeContainer = Color(0xFFF9DEDC),
)

val DarkFinanceColors = FinanceColors(
    positive = Color(0xFF6FD79B),
    negative = Color(0xFFF2B8B5),
    neutral = Color(0xFFAFB1B4),
    positiveContainer = Color(0xFF1B3B29),
    negativeContainer = Color(0xFF4E2321),
)

/** Fixed hues for the four categories in the allocation bar and its legend. */
@Immutable
data class CategoryPalette(val colors: List<Color>)

val LightCategoryPalette = CategoryPalette(
    listOf(
        Color(0xFF3A5BA0),
        Color(0xFFC08A2E),
        Color(0xFF4B8A78),
        Color(0xFF7A4E9B),
    )
)

val DarkCategoryPalette = CategoryPalette(
    listOf(
        Color(0xFF8FA9E0),
        Color(0xFFE0BC70),
        Color(0xFF86C2B2),
        Color(0xFFBA95D8),
    )
)

val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }

val LocalCategoryPalette = staticCompositionLocalOf { LightCategoryPalette }
