package org.basadrito.app.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import basadrito.app.shared.generated.resources.Res
import basadrito.app.shared.generated.resources.settings_back
import org.jetbrains.compose.resources.painterResource

/** Shared native header for Inicio, Cursos and Ajustes. Notifications await their integration. */
@Composable
fun FeatureHeader(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null) {
    Box(Modifier.fillMaxWidth().height(146.dp).clipToBounds().background(Brush.verticalGradient(
        listOf(BasadritoColors.settingsStart, BasadritoColors.settingsMiddle, BasadritoColors.settingsEnd)))) {
        Box(Modifier.offset((-70).dp, (-94).dp).requiredSize(210.dp).background(Color.White.copy(alpha = .16f), CircleShape))
        Box(Modifier.align(Alignment.TopEnd).offset(60.dp, (-14).dp).requiredSize(180.dp).background(Color.White.copy(alpha = .12f), CircleShape))
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack != null) IconButton(onClick = onBack,
                modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = .72f), CircleShape)) {
                Icon(painterResource(Res.drawable.settings_back), "Volver", Modifier.size(22.dp), tint = BasadritoColors.ink)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                subtitle?.let { Text(it, color = BasadritoColors.ink, style = MaterialTheme.typography.bodySmall) }
                Text(title, color = BasadritoColors.ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = {}, enabled = false,
                modifier = Modifier.semantics { contentDescription = "Notificaciones, próximamente" }) {
                // A native outline avoids a remote image dependency or a fictitious unread count.
                Canvas(Modifier.size(22.dp)) {
                    val w = size.width; val h = size.height
                    val bell = Path().apply {
                        moveTo(.2f*w, .75f*h); lineTo(.28f*w, .6f*h); lineTo(.28f*w, .4f*h)
                        cubicTo(.28f*w, .08f*h, .72f*w, .08f*h, .72f*w, .4f*h)
                        lineTo(.72f*w, .6f*h); lineTo(.8f*w, .75f*h); close()
                    }
                    drawPath(bell, BasadritoColors.ink, style = Stroke(1.7.dp.toPx()))
                    drawLine(BasadritoColors.ink, Offset(.43f*w, .9f*h), Offset(.57f*w, .9f*h), 1.7.dp.toPx())
                }
            }
        }
    }
}
