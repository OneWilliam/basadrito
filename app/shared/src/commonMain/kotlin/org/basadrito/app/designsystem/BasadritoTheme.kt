package org.basadrito.app.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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

/** La versión móvil implementa el tema claro documentado en Figma. */
@Composable
fun BasadritoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColorScheme, typography = basadritoTypography(), content = content)
}
