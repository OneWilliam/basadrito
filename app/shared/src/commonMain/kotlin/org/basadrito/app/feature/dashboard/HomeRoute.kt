package org.basadrito.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.basadrito.app.designsystem.*
import org.basadrito.app.domain.auth.AccessMode
import org.basadrito.app.domain.auth.PortalAccess

/** Presents the active account's academic summary, courses and personal tasks. */
@Composable
fun HomeRoute(access: PortalAccess, createViewModel: () -> DashboardViewModel, onCourses: () -> Unit) {
    val model: DashboardViewModel = viewModel { createViewModel() }
    val state by model.state.collectAsStateWithLifecycle()
    val academic by model.academicState.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    FeatureScreen("Hola, ${access.student.fullName.trim().substringBefore(' ')}", subtitle = "Inicio", scrolled = scroll.value > 0) {
      Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        Box(Modifier.fillMaxWidth()) {
          FeatureBackdrop(Modifier.matchParentSize(), home = true)
          Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().align(Alignment.TopCenter).padding(start = 18.dp, end = 18.dp, top = 100.dp, bottom = 24.dp)) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
              Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dashboard académico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Tu progreso académico en un solo lugar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AcademicOverview(academic, state.courses)
                AcademicDataStatus(state, access.mode == AccessMode.LOCAL_ONLY)
                if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.errorMessage != null) TextButton({ model.loadCourses() }, enabled = !state.isLoading) { Text("Reintentar") }
                if (access.mode != AccessMode.LOCAL_ONLY && state.errorMessage == null)
                    TextButton({ model.loadCourses() }, enabled = !state.isLoading) { Text("Actualizar cursos") }
              }
            }
          }
        }
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cursos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onCourses) { Text("Ver cursos") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(state.courses, key = { _, course -> course.id }) { index, course ->
                    Box(Modifier.width(220.dp)) { CourseCard(course, index, academic.snapshot, state.courses) }
                }
            }
            if (state.source != null && state.courses.isEmpty()) Text("No hay cursos disponibles en el aula virtual.",
                color = MaterialTheme.colorScheme.onBackground)
            PendingTasks(academic, state.courses)
            PersonalTasks(model)
        }
      }
    }
}
