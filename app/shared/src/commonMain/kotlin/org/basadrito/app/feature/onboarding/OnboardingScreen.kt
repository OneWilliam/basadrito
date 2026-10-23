package org.basadrito.app.feature.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import basadrito.app.shared.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.basadrito.app.designsystem.BasadritoColors

private data class OnboardingPage(
    val color: Color,
    val eyebrow: String,
    val title: String,
    val description: String,
    val illustration: DrawableResource,
    val indicator: DrawableResource,
)

private val pages = listOf(
    OnboardingPage(Color(0xFFFF9D93), "TODO EN ORDEN", "Tus cursos, en un solo lugar",
        "Revisa notas, sílabos y entregas sin saltar entre plataformas.", Res.drawable.onboarding_core, Res.drawable.onboarding_dots_core),
    OnboardingPage(Color(0xFF90E4C1), "SIEMPRE A TIEMPO", "Gestión y alertas inteligentes",
        "Anticipa parciales, faltas y fechas límite antes de que se conviertan en un problema.", Res.drawable.onboarding_alerts, Res.drawable.onboarding_dots_alerts),
    OnboardingPage(Color(0xFFD8C3F2), "CAMPUS CONECTADO", "Todo tu campus contigo",
        "Encuentra aulas, servicios y personas de la comunidad UNJBG.", Res.drawable.onboarding_campus, Res.drawable.onboarding_dots_campus),
)

@Composable
fun OnboardingScreen(step: Int, onNext: () -> Unit, onBack: () -> Unit) {
    if (step == 0) {
        WelcomeScreen(onNext)
        return
    }
    val page = pages[step - 1]
    Box(Modifier.fillMaxSize().background(page.color), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = 480.dp).fillMaxSize().safeDrawingPadding()) {
            val viewportHeight = maxHeight
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewportHeight)) {
                Image(painterResource(page.illustration), null,
                    Modifier.fillMaxWidth().aspectRatio(390f / 495f), contentScale = ContentScale.FillWidth)
                Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text(page.eyebrow, fontSize = 10.sp, lineHeight = 24.sp, color = BasadritoColors.ink)
                    Text(page.title, style = MaterialTheme.typography.headlineLarge, lineHeight = 36.sp, color = BasadritoColors.ink)
                    Text(page.description, fontSize = 14.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, color = BasadritoColors.ink)
                }
                Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
                Image(painterResource(page.indicator), "Paso $step de 3",
                    Modifier.align(Alignment.CenterHorizontally).size(82.dp, 34.dp))
                Row(Modifier.fillMaxWidth().padding(start = 21.dp, end = 15.dp, top = 12.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    OnboardingButton("Atrás", onBack)
                    OnboardingButton(if (step == 3) "Ingresar" else "Siguiente", onNext)
                }
            }
        }
    }
}

@Composable
private fun OnboardingButton(text: String, onClick: () -> Unit) {
    Button(onClick, Modifier.widthIn(min = 132.dp).heightIn(min = 48.dp), shape = CircleShape,
        colors = ButtonDefaults.buttonColors()) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = 480.dp).fillMaxSize().safeDrawingPadding()) {
            val viewportHeight = maxHeight
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewportHeight),
                horizontalAlignment = Alignment.CenterHorizontally) {
                WelcomeIllustration()
                Spacer(Modifier.weight(1f).heightIn(min = 20.dp))
                Text(buildAnnotatedString {
                    append("Bienvenido a ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Basadrito") }
                }, Modifier.padding(horizontal = 24.dp), fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-1).sp, color = MaterialTheme.colorScheme.onBackground)
                Text("Tu vida universitaria en\nun solo lugar.", Modifier.padding(top = 38.dp, bottom = 60.dp),
                    fontSize = 24.sp, lineHeight = 26.sp, letterSpacing = (-1).sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
                Button(onStart, Modifier.padding(horizontal = 42.dp).fillMaxWidth().heightIn(min = 48.dp),
                    shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B5E), contentColor = BasadritoColors.ink)) {
                    Text("Comenzar", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun WelcomeIllustration() {
    val transition = rememberInfiniteTransition(label = "Órbita de plataformas")
    val angle by transition.animateFloat(0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "Órbita")
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(390f / 475f).clip(RoundedCornerShape(0.dp))) {
        val scale = maxWidth.value / 390f
        fun x(phase: Float) = -108.5f + 108.5f * cos(phase) + 116.5f * sin(phase)
        fun y(phase: Float) = 116.5f + 108.5f * sin(phase) - 116.5f * cos(phase)
        val unjbgPhase = angle + (2 * PI * 28 / 60).toFloat()
        Box(Modifier.offset(((236 + x(unjbgPhase)) * scale).dp, ((62 + y(unjbgPhase)) * scale).dp)
            .size((146 * scale).dp).shadow(4.dp, CircleShape).background(Color(0xFFC0392B), CircleShape).padding((17 * scale).dp)) {
            Image(painterResource(Res.drawable.welcome_unjbg), null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
        }
        Box(Modifier.offset(((229 + x(angle)) * scale).dp, ((55 + y(angle)) * scale).dp)
            .size((153 * scale).dp).shadow(4.dp, CircleShape).background(Color.White, CircleShape).padding((10 * scale).dp)) {
            Box(Modifier.size((139 * scale).dp).clip(RoundedCornerShape(0.dp))) {
                Image(painterResource(Res.drawable.welcome_moodle), null,
                    Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                        .offset((-31.8f * scale).dp, (-32.2f * scale).dp)
                        .size((202.6f * scale).dp), contentScale = ContentScale.FillBounds)
            }
        }
        Image(painterResource(Res.drawable.welcome_logo), null,
            Modifier.offset((92 * scale).dp, (153 * scale).dp).size((205 * scale).dp, (202 * scale).dp), contentScale = ContentScale.FillBounds)
        Surface(Modifier.align(Alignment.TopEnd).padding(16.dp, 6.dp), color = Color(0xFF0A0B0B), shape = CircleShape) {
            Text("ES", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}
