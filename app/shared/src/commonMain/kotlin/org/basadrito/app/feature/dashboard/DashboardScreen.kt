package org.basadrito.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import org.basadrito.app.domain.academic.*
import org.basadrito.app.domain.calendar.institutionToday
import kotlin.time.Clock

@Composable
fun DashboardScreen(courses: List<CourseSummary>, isLoading: Boolean, errorMessage: String?,
    onRetry: () -> Unit, sessionRemembered: Boolean = true, onBack: () -> Unit = {},
    snapshotState: DashboardUiState = DashboardUiState(), localOnly: Boolean = false, academic: AcademicFeed = AcademicFeed()) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Todos") }
    val today = institutionToday()
    val todayIds = courses.filter { course -> academic.snapshot.classes?.any { it.startIso.take(10) == today && matchesCourse(course, it.course) } == true }.map { it.id }.toSet()
    val pendingIds = academic.snapshot.tasks.orEmpty().filter { it.pending == true }.map { it.courseId }.toSet()
    val filtered = remember(courses, query, academic.snapshot.teachers, filter, todayIds, pendingIds) { courses.filter {
        it.name.contains(query, ignoreCase = true) || it.shortName.contains(query, ignoreCase = true) ||
            academic.snapshot.teachers?.get(it.id)?.contains(query, ignoreCase = true) == true
    }.filter { when (filter) { "Hoy" -> it.id in todayIds; "Con pendientes" -> it.id in pendingIds; else -> true } } }
    val scroll = rememberLazyListState()
    val scrolled by remember { derivedStateOf { scroll.firstVisibleItemIndex > 0 || scroll.firstVisibleItemScrollOffset > 0 } }
    FeatureScreen("Cursos", onBack, scrolled = scrolled) {
      LazyColumn(Modifier.fillMaxSize(), state = scroll, contentPadding = PaddingValues(top = 76.dp, bottom = 16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "course-header") {
          Box(Modifier.fillMaxWidth().height(124.dp)) {
            FeatureBackdrop(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Bottom, unbounded = true).height(200.dp))
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
              Text(if (isLoading && snapshotState.source == null) "Consultando cursos…" else "${courses.size} cursos", color = if (LocalDarkAppearance.current) Color.White else BasadritoColors.ink,
                  style = MaterialTheme.typography.labelMedium)
              TextField(query, { query = it }, placeholder = { Text("Buscar cursos") }, singleLine = true,
                  modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
                  colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface,
                      unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                      focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
            }
          }
        }
        item(key = "course-status") {
          Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == "Todos", onClick = { filter = "Todos" }, label = { Text("Todos ${courses.size}") })
                FilterChip(selected = filter == "Hoy", onClick = { filter = "Hoy" }, enabled = academic.snapshot.classes != null,
                    label = { Text("Hoy ${if (academic.snapshot.classes != null) todayIds.size else "—"}") })
                FilterChip(selected = filter == "Con pendientes", onClick = { filter = "Con pendientes" }, enabled = academic.snapshot.tasks != null,
                    label = { Text("Con pendientes ${if (academic.snapshot.tasks != null) pendingIds.size else "—"}") })
            }
            if (!sessionRemembered) Text("No se pudo recordar el acceso. Al volver a abrir la app tendrás que iniciar sesión.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
            AcademicDataStatus(snapshotState, localOnly)
            AcademicWarnings(academic)
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
        }
        itemsIndexed(filtered, key = { _, course -> course.id }) { index, course ->
            Box(Modifier.padding(horizontal = 16.dp)) { CourseCard(course, index, academic.snapshot, courses) }
        }
      }
    }
}

@Composable
internal fun AcademicDataStatus(state: DashboardUiState, localOnly: Boolean) {
    if (!localOnly && !state.refreshFailed && state.cacheWarning == null) return
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
    }
}

@Composable
internal fun CourseCard(course: CourseSummary, index: Int, academic: AcademicSnapshot = AcademicSnapshot(), allCourses: List<CourseSummary> = listOf(course)) {
    val accents = listOf(Color(0xFFB7A1E6), Color(0xFF6FBBE3), Color(0xFF5FD0A3), Color(0xFFF58C80))
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 94.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(academic.teachers?.get(course.id) ?: "Docente sin datos", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val classes = academic.classes?.filter { row -> matchesCourse(course, row.course) && allCourses.count { matchesCourse(it, row.course) } == 1 }
                val details = classes?.map { it.detail }?.filter(String::isNotBlank)?.distinct()
                if (!details.isNullOrEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                if (!classes.isNullOrEmpty()) Text(classes.map { it.day }.distinct().joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                val tasks = academic.tasks?.filter { it.courseId == course.id }
                val known = tasks != null && tasks.none { it.pending == null }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("${if (known) tasks.count { it.pending == true } else "—"} Pendientes", style = MaterialTheme.typography.labelMedium)
                    Text("${if (known) tasks.count { it.pending == true && it.dueAtEpochSeconds?.let { due -> due < Clock.System.now().epochSeconds } == true } else "—"} Sin entregar",
                        style = MaterialTheme.typography.labelMedium)
                }
                val attendance = academic.attendance?.filter { row -> matchesCourse(course, row.course) && allCourses.count { matchesCourse(it, row.course) } == 1 }?.singleOrNull()?.percentage
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Asistencia  ${attendance?.let { academicNumber(it) + " %" } ?: "—"}", style = MaterialTheme.typography.bodySmall)
                if (attendance != null) LinearProgressIndicator(progress = { (attendance / 100).toFloat() }, modifier = Modifier.fillMaxWidth(), color = accents[index % accents.size])
                Box(Modifier.size(width = 30.dp, height = 4.dp).background(accents[index % accents.size], RoundedCornerShape(2.dp)))
            }
        }
    }
}
