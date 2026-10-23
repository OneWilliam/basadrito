package org.basadrito.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.basadrito.app.domain.academic.*
import org.basadrito.app.domain.courses.CourseSummary
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours

internal fun academicNumber(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString()
    else ((value * 100).roundToInt() / 100.0).toString()

/** Exact course code first, then an unambiguous normalized name; never substring matching. */
internal fun matchesCourse(course: CourseSummary, external: String): Boolean {
    val pattern = Regex("\\b\\d{2}\\.\\d{5}\\b")
    val a = pattern.find(course.shortName)?.value ?: pattern.find(course.name)?.value
    val b = pattern.find(external)?.value
    if (a != null && b != null) return a == b
    fun name(value: String) = value.substringAfter(" - ", value).lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]"), "")
    return name(external).isNotEmpty() && name(course.name) == name(external)
}

@Composable
internal fun AcademicOverview(feed: AcademicFeed, courses: List<CourseSummary>) {
    val credits = feed.snapshot.credits
    val dates = courses.mapNotNull { c -> c.startsAtEpochSeconds?.let { start ->
        c.endsAtEpochSeconds?.takeIf { it > start }?.let { start to it } } }.distinct()
    val range = dates.singleOrNull()?.takeIf { dates.size == 1 && courses.all { it.startsAtEpochSeconds == dates[0].first && it.endsAtEpochSeconds == dates[0].second } }
    val now = Clock.System.now().epochSeconds
    val weeks = range?.let { ((it.second - it.first + 604799) / 604800).toInt() }?.takeIf { it in 1..60 }
    val week = if (range != null && weeks != null && now in range.first until range.second)
        ((now - range.first) / 604800).toInt() + 1 else null
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(15.dp)).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Avance curricular", style = MaterialTheme.typography.labelSmall)
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (credits != null) CircularProgressIndicator(progress = { credits.fraction.toFloat() }, modifier = Modifier.fillMaxSize(), strokeWidth = 3.dp)
                Text(credits?.let { "${academicNumber(it.fraction * 100)} %" } ?: "—", style = MaterialTheme.typography.labelSmall)
            }
            Text(credits?.let { "${academicNumber(it.approved)} / ${academicNumber(it.total)} créditos" } ?: "Créditos sin datos",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text("Aprobados / malla", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(15.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Semana académica", style = MaterialTheme.typography.labelSmall)
            Text(week?.let { "Semana $it" } ?: "Sin fechas comunes", fontWeight = FontWeight.Bold)
            if (week != null && weeks != null) LinearProgressIndicator(progress = { week.toFloat() / weeks }, modifier = Modifier.fillMaxWidth())
            Text(if (week != null) "$week de $weeks semanas · fechas de cursos" else "El calendario de cursos no permite calcularla.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    AcademicWarnings(feed)
}

@Composable
internal fun AcademicWarnings(feed: AcademicFeed) {
    if (feed.isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (feed.failedSections.isNotEmpty()) Text("No se pudo actualizar: " + feed.failedSections.joinToString { when (it) {
        AcademicSection.CREDITS -> "créditos"; AcademicSection.SCHEDULE -> "horario"
        AcademicSection.ATTENDANCE -> "asistencia"; AcademicSection.TEACHERS -> "docentes"; AcademicSection.TASKS -> "tareas"
    } }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    if (feed.storageFailed) Text("No se pudo leer o guardar parte de la copia sin conexión.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
internal fun PendingTasks(feed: AcademicFeed, courses: List<CourseSummary>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Text("Tareas pendientes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    val tasks = feed.snapshot.tasks
    val available = tasks?.filter { it.pending == true }?.sortedBy { it.dueAtEpochSeconds ?: Long.MAX_VALUE }
    when {
        tasks == null -> Text("Las tareas aún no están disponibles.", style = MaterialTheme.typography.bodySmall)
        available.isNullOrEmpty() && tasks.any { it.pending == null } -> Text("No se pudo comprobar el estado de las entregas.")
        available.isNullOrEmpty() -> Text("No hay tareas pendientes.")
        else -> Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                (if (expanded) available else available.take(5)).forEach { task ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(task.name, fontWeight = FontWeight.SemiBold)
                        Text(courses.firstOrNull { it.id == task.courseId }?.name ?: "Aula virtual", style = MaterialTheme.typography.bodySmall)
                        task.dueAtEpochSeconds?.let { Text("Entrega: ${(Instant.fromEpochSeconds(it) - 5.hours).toString().take(16).replace('T', ' ')}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (available.size > 5) TextButton({ expanded = !expanded }) {
                    Text(if (expanded) "Ver menos" else "Ver todas (${available.size})")
                }
            }
        }
    }
}
