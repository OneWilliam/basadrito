package org.basadrito.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.basadrito.app.designsystem.*
import org.basadrito.app.domain.auth.AccessMode
import org.basadrito.app.domain.auth.PortalAccess

@Composable
fun SettingsScreen(access: PortalAccess, onBack: () -> Unit, themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit, onReconnect: () -> Unit, onSignOut: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState())) {
        FeatureHeader("Ajustes", onBack)
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = colors.surface) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val initials = access.student.fullName.trim().split(Regex("\\s+"))
                        .filter(String::isNotBlank).take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
                    Box(Modifier.size(46.dp).background(BasadritoColors.profileAvatar, CircleShape), contentAlignment = Alignment.Center) {
                        Text(initials, color = BasadritoColors.ink, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(access.student.fullName, fontWeight = FontWeight.SemiBold)
                        Text(access.student.username, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("APARIENCIA", style = MaterialTheme.typography.labelMedium, color = colors.onBackground)
            Surface(shape = RoundedCornerShape(20.dp), color = colors.surface) {
                Column(Modifier.selectableGroup().fillMaxWidth()) {
                    ThemeMode.entries.forEach { mode ->
                        Row(Modifier.fillMaxWidth().selectable(themeMode == mode, role = Role.RadioButton,
                            onClick = { onThemeChange(mode) }).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = themeMode == mode, onClick = null)
                            Text(mode.label, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
            Text("CUENTA", style = MaterialTheme.typography.labelMedium, color = colors.onBackground)
            Surface(shape = RoundedCornerShape(20.dp), color = colors.surface) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (access.mode == AccessMode.LOCAL_ONLY) "Lectura local sin conexión. El acceso a los portales aún no se ha comprobado."
                        else if (access.sessionRemembered) "Tu acceso está guardado en este dispositivo."
                        else "No se pudo guardar el acceso. Tendrás que iniciar sesión al volver a abrir la app.")
                    if (access.mode == AccessMode.LOCAL_ONLY) TextButton(onClick = onReconnect) { Text("Comprobar conexión") }
                    Text("Al cerrar sesión se conserva lo descargado de esta cuenta, bloqueado hasta que vuelvas a identificarte.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error)) {
                Text("Cerrar sesión", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
