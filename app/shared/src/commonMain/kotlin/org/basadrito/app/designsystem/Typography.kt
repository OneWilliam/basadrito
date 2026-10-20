package org.basadrito.app.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import basadrito.app.shared.generated.resources.Res
import basadrito.app.shared.generated.resources.inter_variable
import org.jetbrains.compose.resources.Font

/** Inter se incluye localmente; no requiere descargar fuentes durante el uso. */
@Composable
internal fun basadritoTypography(): Typography {
    val inter = FontFamily(
        Font(Res.font.inter_variable, weight = FontWeight.Normal),
        Font(Res.font.inter_variable, weight = FontWeight.Medium),
        Font(Res.font.inter_variable, weight = FontWeight.SemiBold),
        Font(Res.font.inter_variable, weight = FontWeight.Bold),
    )
    val defaults = Typography()
    return defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = inter, letterSpacing = 0.sp),
        displayMedium = defaults.displayMedium.copy(fontFamily = inter, letterSpacing = 0.sp),
        displaySmall = defaults.displaySmall.copy(fontFamily = inter, letterSpacing = 0.sp),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = inter, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.42).sp),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = inter, letterSpacing = 0.sp),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = inter, letterSpacing = 0.sp),
        titleLarge = defaults.titleLarge.copy(fontFamily = inter, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = defaults.titleMedium.copy(fontFamily = inter, letterSpacing = 0.sp),
        titleSmall = defaults.titleSmall.copy(fontFamily = inter, letterSpacing = 0.sp),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = inter, letterSpacing = 0.sp),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = inter, letterSpacing = 0.sp),
        bodySmall = defaults.bodySmall.copy(fontFamily = inter, letterSpacing = 0.sp),
        labelLarge = defaults.labelLarge.copy(fontFamily = inter, letterSpacing = 0.sp),
        labelMedium = defaults.labelMedium.copy(fontFamily = inter, letterSpacing = 0.sp),
        labelSmall = defaults.labelSmall.copy(fontFamily = inter, letterSpacing = 0.sp),
    )
}
