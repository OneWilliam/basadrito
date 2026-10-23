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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.basadrito.app.designsystem.FeatureHeader
import org.basadrito.app.domain.auth.AccessMode
import org.basadrito.app.domain.auth.PortalAccess

/** Inicio uses the available course integration; academic progress and schedules await real data. */
@Composable
fun HomeRoute(access: PortalAccess, createViewModel: () -> DashboardViewModel, onCourses: () -> Unit) {
    val model: DashboardViewModel = viewModel { createViewModel() }
    val state by model.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        FeatureHeader("Hola, ${access.student.fullName.trim().substringBefore(' ')}", subtitle = "Inicio")
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tu aula virtual", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (state.source != null) Text("${state.courses.size} cursos disponibles", style = MaterialTheme.typography.headlineSmall)
                    AcademicDataStatus(state, access.mode == AccessMode.LOCAL_ONLY)
                    if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.errorMessage != null) TextButton({ model.loadCourses() }, enabled = !state.isLoading) { Text("Reintentar") }
                    TextButton(onCourses) { Text("Ver cursos") }
                    if (access.mode != AccessMode.LOCAL_ONLY && state.errorMessage == null)
                        TextButton({ model.loadCourses() }, enabled = !state.isLoading) { Text("Actualizar cursos") }
                }
            }
            Text("Tus cursos", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            state.courses.take(3).forEachIndexed { index, course -> CourseCard(course, index) }
            if (state.source != null && state.courses.isEmpty()) Text("No hay cursos disponibles en el aula virtual.",
                color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
