package org.basadrito.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.basadrito.app.designsystem.*
import org.basadrito.app.domain.courses.CourseSummary
import org.basadrito.app.domain.courses.CourseSource
import org.basadrito.app.domain.courses.CourseCacheWarning
import kotlin.time.Instant

@Composable
fun DashboardScreen(courses: List<CourseSummary>, isLoading: Boolean, errorMessage: String?,
    onRetry: () -> Unit, sessionRemembered: Boolean = true, onBack: () -> Unit = {},
    snapshotState: DashboardUiState = DashboardUiState(), localOnly: Boolean = false) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(courses, query) { courses.filter {
        it.name.contains(query, ignoreCase = true) || it.shortName.contains(query, ignoreCase = true)
    } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FeatureHeader("Mis cursos", onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!sessionRemembered) Text("No se pudo recordar el acceso. Al volver a abrir la app tendrás que iniciar sesión.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
            OutlinedTextField(query, { query = it }, label = { Text("Buscar cursos") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            AcademicDataStatus(snapshotState, localOnly)
            if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!localOnly && errorMessage == null) TextButton(onRetry, enabled = !isLoading) { Text("Actualizar cursos") }
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
                TextButton(onRetry, enabled = !isLoading) { Text("Reintentar") }
            }
            if (!isLoading && errorMessage == null && filtered.isEmpty()) Text(
                if (query.isBlank()) "No hay cursos disponibles en el aula virtual." else "No se encontraron cursos.",
                color = MaterialTheme.colorScheme.onBackground)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(filtered, key = { _, course -> course.id }) { index, course -> CourseCard(course, index) }
        }
    }
}

@Composable
internal fun AcademicDataStatus(state: DashboardUiState, localOnly: Boolean) {
    if (state.source == null && !localOnly && state.cacheWarning == null) return
    val status = when {
        localOnly -> "Sin conexión · lectura local de esta cuenta"
        state.refreshFailed -> "No se pudo actualizar · mostrando cursos guardados"
        state.source == CourseSource.CACHE -> "Cursos guardados en este dispositivo"
        else -> "Cursos actualizados desde Moodle"
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (state.source != null || localOnly)
            Text(status, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
        state.cacheWarning?.let { warning ->
            Text(when (warning) {
                CourseCacheWarning.READ_FAILED -> "No se pudo leer la copia anterior de los cursos."
                CourseCacheWarning.WRITE_FAILED -> "Los cursos se pueden consultar, pero no quedaron guardados para usarlos sin conexión."
            }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        state.updatedAtMillis?.let {
            Text("Última actualización: ${Instant.fromEpochMilliseconds(it).toString().take(16).replace('T', ' ')} UTC",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun CourseCard(course: CourseSummary, index: Int) {
    val accents = listOf(Color(0xFFB7A1E6), Color(0xFF6FBBE3), Color(0xFF5FD0A3), Color(0xFFF58C80))
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 94.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(8.dp).height(74.dp).background(accents[index % accents.size]))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (course.shortName.isNotBlank() && course.shortName != course.name)
                    Text(course.shortName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
