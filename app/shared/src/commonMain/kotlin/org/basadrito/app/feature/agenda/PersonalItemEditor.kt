package org.basadrito.app.feature.agenda

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.basadrito.app.domain.calendar.*

@Composable
fun PersonalItemEditor(item: PersonalItem, onDismiss: () -> Unit,
    onSave: suspend (PersonalItem) -> Result<Unit>, onDelete: suspend (String) -> Result<Unit>) {
    var title by rememberSaveable(item.id) { mutableStateOf(item.title) }
    var description by rememberSaveable(item.id) { mutableStateOf(item.description) }
    var date by rememberSaveable(item.id) { mutableStateOf(item.date.orEmpty()) }
    var time by rememberSaveable(item.id) { mutableStateOf(item.time.orEmpty()) }
    var kind by rememberSaveable(item.id) { mutableStateOf(item.kind) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (item.id.isBlank()) "Agregar a mi agenda" else "Editar mi agenda") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { PersonalKind.entries.forEach { option ->
                FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(when (option) {
                    PersonalKind.TASK -> "Tarea"; PersonalKind.EVENT -> "Evento"; PersonalKind.EXAM -> "Examen" }) })
            } }
            OutlinedTextField(title, { title = it }, label = { Text("Título") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Descripción") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(date, { date = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(time, { time = it }, label = { Text("Hora (HH:mm), opcional") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            if (kind == PersonalKind.TASK) Text("Puedes dejar la tarea sin fecha. Al añadir una fecha aparecerá en Calendario.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (item.id.isNotBlank()) TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch { onDelete(item.id).fold(onSuccess = { onDismiss() }, onFailure = { error = "No se pudo eliminar. Intenta nuevamente." }); busy = false }
            }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(enabled = !busy, onClick = {
            val edited = item.copy(title = title.trim(), description = description.trim(), kind = kind,
                date = date.trim().ifBlank { null }, time = time.trim().ifBlank { null })
            val validation = runCatching { edited.validate() }
            if (validation.isFailure) error = validation.exceptionOrNull()?.message else {
                busy = true
                scope.launch { onSave(edited).fold(onSuccess = { onDismiss() }, onFailure = { error = "No se pudo guardar. Intenta nuevamente." }); busy = false }
            }
        }) { Text(if (busy) "Guardando…" else "Guardar") } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}
