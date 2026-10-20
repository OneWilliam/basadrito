package org.basadrito.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.basadrito.app.designsystem.BasadritoTokens
import org.basadrito.app.domain.courses.CourseSummary

/** Inicio blanco limitado a la lista de cursos disponible en la integración actual. */
@Composable
fun DashboardScreen(
    courses: List<CourseSummary>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeContentPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = BasadritoTokens.pagePadding),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Mis cursos", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onSignOut) { Text("Cerrar sesión") }
        }
        if (isLoading) Row(Modifier.padding(BasadritoTokens.pagePadding), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Cargando cursos…")
        }
        if (errorMessage != null) Column(Modifier.padding(BasadritoTokens.pagePadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRetry, enabled = !isLoading) { Text("Reintentar") }
        } else if (!isLoading && courses.isEmpty()) {
            Text("No hay cursos disponibles en el aula virtual.", Modifier.padding(BasadritoTokens.pagePadding),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = BasadritoTokens.pagePadding, vertical = 12.dp)) {
            items(courses, key = { it.id }) { course ->
                ListItem(
                    headlineContent = { Text(course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
                    supportingContent = if (course.shortName.isNotBlank() && course.shortName != course.name) {
                        { Text(course.shortName, style = MaterialTheme.typography.bodySmall) }
                    } else null,
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
