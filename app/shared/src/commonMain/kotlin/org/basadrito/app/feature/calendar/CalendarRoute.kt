package org.basadrito.app.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.basadrito.app.designsystem.*
import org.basadrito.app.domain.calendar.*
import org.basadrito.app.feature.agenda.PersonalItemEditor

@Composable
fun CalendarRoute(createViewModel: () -> CalendarViewModel, onBack: () -> Unit) {
    val model: CalendarViewModel = viewModel { createViewModel() }
    val month by model.month.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val own by model.personalItems.collectAsStateWithLifecycle()
    val error by model.errorMessage.collectAsStateWithLifecycle()
    var day by rememberSaveable(month.key) { mutableStateOf(institutionToday().takeIf { it.startsWith(month.key) } ?: month.firstDate) }
    var filter by rememberSaveable { mutableStateOf("Todo") }
    var edit by remember { mutableStateOf<PersonalItem?>(null) }
    var detail by remember { mutableStateOf<CalendarEvent?>(null) }
    edit?.let { PersonalItemEditor(it, { edit = null }, model::save, model::delete) }
    detail?.let { event -> AlertDialog(onDismissRequest = { detail = null }, title = { Text(event.title) },
        text = { Text("${event.date} ${event.time.orEmpty()}\n${event.origin.label()}\n${event.detail}") },
        confirmButton = { TextButton({ detail = null }) { Text("Cerrar") } }) }
    val scroll = rememberScrollState()
    FeatureScreen("Calendario", onBack, scrolled = scroll.value > 0) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                Box(Modifier.fillMaxWidth()) {
                    FeatureBackdrop(Modifier.matchParentSize())
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 88.dp, bottom = 12.dp)) {
                        Text("${state.events.size} actividades este mes", color = if (LocalDarkAppearance.current) MaterialTheme.colorScheme.onBackground else BasadritoColors.ink)
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.padding(10.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    IconButton({ model.moveMonth(-1) }, Modifier.semantics { contentDescription = "Mes anterior" }) { Text("‹") }
                                    Text("${monthNames[month.month - 1]} ${month.year}", fontWeight = FontWeight.SemiBold)
                                    IconButton({ model.moveMonth(1) }, Modifier.semantics { contentDescription = "Mes siguiente" }) { Text("›") }
                                }
                                Row { listOf("L", "M", "X", "J", "V", "S", "D").forEach { Text(it, Modifier.weight(1f).wrapContentWidth(), style = MaterialTheme.typography.labelSmall) } }
                                repeat((month.mondayOffset + month.days + 6) / 7) { row ->
                                    Row {
                                        repeat(7) { column ->
                                            val value = row * 7 + column - month.mondayOffset + 1
                                            Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                                                if (value in 1..month.days) {
                                                    val date = month.date(value)
                                                    val hasEvents = state.events.any { it.date == date }
                                                    TextButton({ day = date }, modifier = Modifier.size(42.dp), contentPadding = PaddingValues(0.dp),
                                                        colors = ButtonDefaults.textButtonColors(containerColor = if (day == date) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                            contentColor = if (day == date) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Text(value.toString())
                                                            if (hasEvents) Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Todo", "Clases", "Moodle", "Mis eventos").forEach { option ->
                        FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) })
                    } }
                    if (state.isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.refreshFailed) Text("Alguna fuente no pudo actualizarse. Se conserva la copia disponible.", color = MaterialTheme.colorScheme.error)
                    if (state.storageFailed) Text("No se pudo leer o guardar parte de la agenda.", color = MaterialTheme.colorScheme.error)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!state.hasSchedule || !state.hasTasks) Text("${if (!state.hasSchedule) "Horario no disponible. " else ""}${if (!state.hasTasks) "Entregas no disponibles." else ""}", style = MaterialTheme.typography.bodySmall)
                    Text(day, fontWeight = FontWeight.SemiBold)
                    val events = state.events.filter { it.date == day && when (filter) {
                        "Clases" -> it.origin == CalendarOrigin.CLASS; "Moodle" -> it.origin == CalendarOrigin.MOODLE
                        "Mis eventos" -> it.origin in setOf(CalendarOrigin.PERSONAL_TASK, CalendarOrigin.PERSONAL_EVENT, CalendarOrigin.PERSONAL_EXAM)
                        else -> true
                    } }
                    if (events.isEmpty()) Text("No hay actividades para este día y filtro.")
                    events.forEach { event -> Surface(onClick = {
                        if (event.id.startsWith("personal:")) edit = own.firstOrNull { it.id == event.id.removePrefix("personal:") }
                        else detail = event
                    }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(event.title, fontWeight = FontWeight.SemiBold)
                            Text("${event.time.orEmpty()} · ${event.origin.label()}", style = MaterialTheme.typography.bodySmall)
                            if (event.detail.isNotBlank()) Text(event.detail, style = MaterialTheme.typography.bodySmall)
                            event.completed?.let { Text(if (it) "Completada" else "Pendiente", style = MaterialTheme.typography.labelSmall) }
                        }
                    } }
                    TextButton({ model.reload(includeTasks = true) }, enabled = !state.isRefreshing) { Text("Actualizar agenda") }
                }
            }
            Button({ edit = PersonalItem("", "", kind = PersonalKind.EVENT, date = day) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp)) { Text("+ Agregar evento o tarea") }
        }
    }
}

private val monthNames = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
private fun CalendarOrigin.label(): String = when (this) {
    CalendarOrigin.CLASS -> "Clase · Intranet"; CalendarOrigin.MOODLE -> "Moodle"
    CalendarOrigin.PERSONAL_TASK -> "Mi tarea"; CalendarOrigin.PERSONAL_EVENT -> "Mi evento"; CalendarOrigin.PERSONAL_EXAM -> "Mi examen"
}
