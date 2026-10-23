package org.basadrito.app.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale

/** Figma 565:2338: rounded square, angular fill and tassel drawn with native primitives. */
@Composable
fun DarkBrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.width / 143f, size.height / 155f, pivot = Offset.Zero) {
            val gold = Color(0xFFFFD700)
            drawCircle(gold, 6.8f, Offset(125.43f, 139.4f))
            drawRoundRect(gold, Offset(121.35f, 76.84f), Size(7.48f, 64.6f), CornerRadius(3.74f))
            rotate(45f, Offset(71.2109f, 1.15991f)) {
                drawRoundRect(Brush.sweepGradient(0f to Color(0xFF0A0A0B), .47115385f to Color(0xFFE72932),
                    1f to Color(0xFF0A0A0B), center = Offset(125.6164f, 55.5654f)),
                    topLeft = Offset(71.2109f, 1.15991f), size = Size(108.811f, 108.811f), cornerRadius = CornerRadius(20.4f))
            }
            drawOval(gold, Offset(121.35f, 76.84f), Size(6.8f, 6.12f))
        }
    }
}
