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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    if (showAppearance) AlertDialog(onDismissRequest = { showAppearance = false },
        title = { Text("Apariencia") }, confirmButton = { TextButton({ showAppearance = false }) { Text("Listo") } },
        text = {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth().selectable(themeMode == mode, role = Role.RadioButton,
                        onClick = { onThemeChange(mode); showAppearance = false }).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = themeMode == mode, onClick = null)
                        Text(mode.label, Modifier.padding(start = 12.dp))
                    }
                }
            }
        })
    val scroll = rememberScrollState()
    FeatureScreen("Ajustes", onBack, scrolled = scroll.value > 0) {
      Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 208.dp)) {
          FeatureBackdrop(Modifier.fillMaxWidth().height(184.dp))
          Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.TopCenter)
              .padding(start = 16.dp, end = 16.dp, top = 120.dp, bottom = 12.dp)) {
            ProfileCard(access)
          }
        }
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("APARIENCIA", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Surface(onClick = { showAppearance = true }, shape = RoundedCornerShape(18.dp), color = colors.surface) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Modo oscuro", style = MaterialTheme.typography.bodyMedium)
                        Text("Claro, oscuro o según el sistema", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Text(themeMode.label, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text("CUENTAS VINCULADAS", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Surface(shape = RoundedCornerShape(18.dp), color = colors.surface) {
                Column(Modifier.fillMaxWidth()) {
                    LinkedAccount("Moodle (Aula Virtual)", access.mode != AccessMode.LOCAL_ONLY)
                    HorizontalDivider(color = colors.outlineVariant)
                    LinkedAccount("Intranet UNJBG", access.intranetAvailable)
                }
            }
            Text("DATOS", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
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
}

@Composable
private fun LinkedAccount(title: String, connected: Boolean) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(if (connected) "Conectado" else "Sin conexión", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ProfileCard(access: PortalAccess) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surface, shadowElevation = 6.dp) {
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
}
