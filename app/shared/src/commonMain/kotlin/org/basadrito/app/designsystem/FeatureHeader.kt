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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Only the controls stay pinned; the caller scrolls the backdrop and its cards. */
@Composable
fun FeatureScreen(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null,
    scrolled: Boolean = false, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        content()
        Surface(color = if (scrolled) MaterialTheme.colorScheme.background else Color.Transparent,
            shadowElevation = if (scrolled) 2.dp else 0.dp) {
            FeatureHeader(title, onBack, subtitle, scrolled)
        }
    }
}

@Composable
fun FeatureBackdrop(modifier: Modifier = Modifier, home: Boolean = false) {
    val stops = if (LocalDarkAppearance.current) {
        if (home) arrayOf(0f to Color(0xFF261ED2), .54f to Color(0xFF27438D), 1f to Color(0xFF2027A4))
        else arrayOf(0f to Color(0xFF27448D), .30f to Color(0xFF241ED2), 1f to Color(0xFF2027A4))
    } else {
        if (home) arrayOf(0f to Color(0xFFA39FFF), .54f to Color(0xFF6282D4), 1f to Color(0xFF6F75E3))
        else arrayOf(0f to Color(0xFF6282D4), .30f to Color(0xFFA39FFF), 1f to Color(0xFF6F75E3))
    }
    Box(modifier.clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
        .clipToBounds().background(Brush.verticalGradient(*stops))) {
        Box(Modifier.offset((-70).dp, (-94).dp).requiredSize(210.dp).background(Color.White.copy(alpha = .16f), CircleShape))
        Box(Modifier.align(Alignment.TopEnd).offset(60.dp, (-14).dp).requiredSize(180.dp).background(Color.White.copy(alpha = .16f), CircleShape))
    }
}

/** Native controls shared by Inicio, Cursos and Ajustes. */
@Composable
fun FeatureHeader(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null, scrolled: Boolean = false) {
    val ink = if (scrolled) MaterialTheme.colorScheme.onBackground else if (LocalDarkAppearance.current) Color(0xFFF7F7F8) else BasadritoColors.ink
    val button = Color.White.copy(alpha = if (LocalDarkAppearance.current) .20f else .72f)
    Box(Modifier.fillMaxWidth().heightIn(min = 76.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack != null) IconButton(onClick = onBack,
                modifier = Modifier.size(44.dp).background(button, CircleShape)) {
                Icon(painterResource(Res.drawable.settings_back), "Volver", Modifier.size(22.dp), tint = ink)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                subtitle?.let { Text(it, color = ink, style = MaterialTheme.typography.labelMedium) }
                Text(title, color = ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = {}, enabled = false,
                modifier = Modifier.size(44.dp).background(button, CircleShape).semantics { contentDescription = "Notificaciones, próximamente" }) {
                // A native outline avoids a remote image dependency or a fictitious unread count.
                Canvas(Modifier.size(22.dp)) {
                    val w = size.width; val h = size.height
                    val bell = Path().apply {
                        moveTo(.2f*w, .75f*h); lineTo(.28f*w, .6f*h); lineTo(.28f*w, .4f*h)
                        cubicTo(.28f*w, .08f*h, .72f*w, .08f*h, .72f*w, .4f*h)
                        lineTo(.72f*w, .6f*h); lineTo(.8f*w, .75f*h); close()
                    }
                    drawPath(bell, ink, style = Stroke(1.7.dp.toPx()))
                    drawLine(ink, Offset(.43f*w, .9f*h), Offset(.57f*w, .9f*h), 1.7.dp.toPx())
                }
            }
        }
    }
}
