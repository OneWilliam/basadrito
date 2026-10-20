package org.basadrito.app.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import basadrito.app.shared.generated.resources.*
import org.basadrito.app.designsystem.*
import org.jetbrains.compose.resources.painterResource

/** Frame 373:518: se conserva el formulario real de Moodle e Intranet. */
@Composable
fun LoginScreen(
    state: AuthUiState,
    onUsernameChange: (String) -> Unit,
    onMoodlePasswordChange: (String) -> Unit,
    onIntranetPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val canSubmit = state.username.isNotBlank() && state.moodlePassword.isNotBlank() && !state.isLoading
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        BasadritoColors.loginStart, BasadritoColors.loginMiddle, BasadritoColors.loginEnd))),
        contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().fillMaxHeight().imePadding()
            .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
            LoginHero()
            Surface(Modifier.fillMaxWidth(), color = BasadritoColors.white,
                shape = RoundedCornerShape(topStart = BasadritoTokens.sheetRadius, topEnd = BasadritoTokens.sheetRadius)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = BasadritoTokens.formPadding).padding(top = 28.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(BasadritoTokens.fieldGap)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Inicia sesión", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.24).sp), color = BasadritoColors.ink)
                        Text("Usa tus credenciales institucionales", style = MaterialTheme.typography.bodyMedium, color = BasadritoColors.loginSecondaryText)
                    }
                    BasadritoTextField(state.username, onUsernameChange, "Código universitario", "Ingresa tu código", Res.drawable.icon_user, !state.isLoading)
                    BasadritoTextField(state.moodlePassword, onMoodlePasswordChange, "Contraseña Moodle", "Ingresa tu contraseña", Res.drawable.icon_lock, !state.isLoading, isPassword = true)
                    BasadritoTextField(state.intranetPassword, onIntranetPasswordChange, "Contraseña Intranet", "Vacía: usar la de Moodle", Res.drawable.icon_lock, !state.isLoading,
                        isPassword = true, imeAction = ImeAction.Done, onSubmit = { if (canSubmit) onLogin() })
                    TextButton(onClick = { uriHandler.openUri("https://campus.unjbg.edu.pe/") },
                        modifier = Modifier.align(Alignment.End), enabled = !state.isLoading,
                        contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.textButtonColors(contentColor = BasadritoColors.recoveryLink)) {
                        Text("¿Olvidaste tu contraseña?", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                    }
                    state.errorMessage?.let { error ->
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Text(error, Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    BasadritoPrimaryButton(canSubmit, state.isLoading, state.loadingMessage, onLogin)
                    Text("Términos de uso y condiciones · Política de privacidad", Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall, color = BasadritoColors.inputHint, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun LoginHero() {
    // Figma incluye 44 px de barra de estado: la aplicación usa los insets reales del sistema.
    Box(Modifier.fillMaxWidth().height(293.dp).clipToBounds()) {
        Image(painterResource(Res.drawable.login_deco_left), null, Modifier.offset(x = (-70).dp, y = (-24).dp).size(240.dp))
        Image(painterResource(Res.drawable.login_deco_right), null, Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = 66.dp).size(180.dp))
        Image(painterResource(Res.drawable.login_deco_small), null, Modifier.align(Alignment.TopEnd).offset(x = (-30).dp, y = (-24).dp).size(60.dp))
        Image(painterResource(Res.drawable.login_glow), null, Modifier.align(Alignment.TopCenter).offset(y = (-54).dp).size(300.dp))
        Image(painterResource(Res.drawable.logo), "Logo de Basadrito", contentScale = ContentScale.FillBounds, modifier = Modifier.align(Alignment.TopCenter).offset(y = 36.dp).size(width = 154.dp, height = 141.dp))
        Text("Basadrito", Modifier.align(Alignment.TopCenter).offset(y = 194.dp),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.72).sp),
            color = BasadritoColors.ink)
    }
}
