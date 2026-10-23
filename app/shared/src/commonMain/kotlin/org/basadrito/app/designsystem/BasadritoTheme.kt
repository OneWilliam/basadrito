package org.basadrito.app.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = BasadritoColors.ink,
    onPrimary = BasadritoColors.white,
    primaryContainer = BasadritoColors.neutralSurface,
    onPrimaryContainer = BasadritoColors.ink,
    secondary = BasadritoColors.secondaryText,
    onSecondary = BasadritoColors.white,
    background = BasadritoColors.white,
    onBackground = BasadritoColors.ink,
    surface = BasadritoColors.white,
    onSurface = BasadritoColors.ink,
    surfaceVariant = BasadritoColors.neutralSurface,
    onSurfaceVariant = BasadritoColors.secondaryText,
    outline = BasadritoColors.inputBorder,
    outlineVariant = BasadritoColors.subtleBorder,
    error = BasadritoColors.error,
    onError = BasadritoColors.white,
    errorContainer = BasadritoColors.errorContainer,
    onErrorContainer = BasadritoColors.error,
)

// Figma Colores / Light and Dark. Individual screens consume roles, not raw swatches.
val DarkColorScheme = darkColorScheme(
    primary = Color.White, onPrimary = Color(0xFF0A0A0B),
    background = Color(0xFF111113), onBackground = Color(0xFFF7F7F8),
    surface = Color(0xFF1E1E25), onSurface = Color(0xFFF7F7F8),
    surfaceContainer = Color(0xFF28282E),
    surfaceVariant = Color(0xFF28282E), onSurfaceVariant = Color(0xFFB0B0B8),
    outline = Color(0xFF77777E), outlineVariant = Color(0xFF33333A),
    error = Color(0xFFE58073), onError = Color(0xFF360B08),
    errorContainer = Color(0xFF522522), onErrorContainer = Color(0xFFFFDAD4),
)

val LocalDarkAppearance = staticCompositionLocalOf { false }

/** Special roles for the light inputs shown in the dark login mockup. */
object LoginColors {
    val fieldText = Color(0xFF141416)
    val fieldSurface = Color(0xFFF7F3F2)
    val fieldHint = Color(0xFF746B69)
}

@Composable
fun BasadritoTheme(mode: ThemeMode = ThemeMode.SYSTEM, onDarkChanged: (Boolean) -> Unit = {}, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    SideEffect { onDarkChanged(dark) }
    CompositionLocalProvider(LocalDarkAppearance provides dark) {
        MaterialTheme(colorScheme = if (dark) DarkColorScheme else LightColorScheme, typography = basadritoTypography(), content = content)
    }
}
