package org.basadrito.app.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.basadrito.app.domain.calendar.*
import org.basadrito.app.feature.agenda.PersonalItemEditor

@Composable
internal fun PersonalTasks(model: DashboardViewModel) {
    val items by model.personalItems.collectAsStateWithLifecycle()
    val storageError by model.personalError.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PersonalItem?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    editing?.let { PersonalItemEditor(it, { editing = null }, model::savePersonal, model::deletePersonal) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Mis tareas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        TextButton({ editing = PersonalItem("", "", kind = PersonalKind.TASK) }) { Text("Agregar tarea") }
    }
    val tasks = items.filter { it.kind == PersonalKind.TASK }.sortedWith(compareBy<PersonalItem> { it.completed }.thenBy { it.date ?: "9999" })
    (error ?: storageError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (tasks.isEmpty()) Text("Todavía no has agregado tareas propias.", style = MaterialTheme.typography.bodySmall)
    tasks.forEach { item ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(item.completed, onCheckedChange = { completed -> scope.launch {
                model.savePersonal(item.copy(completed = completed)).fold(onSuccess = { error = null },
                    onFailure = { error = "No se pudo cambiar el estado de la tarea." })
            } })
            TextButton({ editing = item }, Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(item.title)
                    item.date?.let { Text("$it ${item.time.orEmpty()}", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}
