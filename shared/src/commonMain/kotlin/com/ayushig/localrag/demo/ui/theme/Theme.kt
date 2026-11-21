package com.ayushig.localrag.demo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

/** Platform dynamic colour: Material You on Android 12+, absent everywhere else. */
internal expect @Composable fun dynamicColorScheme(darkTheme: Boolean): ColorScheme?

@Composable
fun LocalRAGTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val dynamicScheme = if (dynamicColor) dynamicColorScheme(darkTheme) else null
    val colorScheme = dynamicScheme ?: if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalFinanceColors provides if (darkTheme) DarkFinanceColors else LightFinanceColors,
        LocalCategoryPalette provides if (darkTheme) DarkCategoryPalette else LightCategoryPalette,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
