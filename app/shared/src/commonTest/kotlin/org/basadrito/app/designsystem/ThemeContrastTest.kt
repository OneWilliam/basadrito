package org.basadrito.app.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

class ThemeContrastTest {
    @Test fun darkTextRolesAndLightInputsRemainLegible() {
        val dark = DarkColorScheme
        for ((foreground, background) in listOf(dark.onBackground to dark.background,
            dark.onSurface to dark.surface, dark.onSurfaceVariant to dark.surface,
            dark.onPrimary to dark.primary, dark.onErrorContainer to dark.errorContainer,
            LoginColors.fieldText to LoginColors.fieldSurface, LoginColors.fieldHint to LoginColors.fieldSurface)) {
            assertTrue(contrast(foreground, background) >= 4.5f)
        }
    }

    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
}
