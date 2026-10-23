package org.basadrito.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.basadrito.app.domain.calendar.*

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val repository: CalendarRepository = NoCalendarRepository,
    private val personal: PersonalAgendaRepository = NoPersonalAgendaRepository, scope: CoroutineScope? = null) : ViewModel() {
    private val work = scope ?: viewModelScope
    private val selected = MutableStateFlow(CalendarMonth.containing(institutionToday()))
    val month = selected.asStateFlow()
    private val feed = MutableStateFlow(CalendarFeed())
    val state = feed.asStateFlow()
    private val own = MutableStateFlow(emptyList<PersonalItem>())
    val personalItems = own.asStateFlow()
    private val error = MutableStateFlow<String?>(null)
    val errorMessage = error.asStateFlow()
    private val revision = MutableStateFlow(0)
    private var refresh: Job? = null
    private val observation = work.launch {
        combine(selected, revision) { month, _ -> month }.flatMapLatest { month -> repository.observe(month).catch { error.value = "No se pudo abrir la agenda. Intenta nuevamente." } }
            .collect { feed.value = it }
    }
    private val personalObservation = work.launch {
        revision.flatMapLatest { personal.observe().catch { error.value = "No se pudieron abrir tus eventos y tareas." } }.collect { own.value = it }
    }
    init { reload() }
    fun moveMonth(delta: Int) { selected.value = selected.value.shift(delta); reload() }
    fun reload(includeTasks: Boolean = false) {
        refresh?.cancel()
        revision.update { it + 1 }
        refresh = work.launch {
            error.value = null
            try { repository.refresh(selected.value, refreshTasks = includeTasks) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error.value = "No se pudo actualizar la agenda. Intenta nuevamente." }
        }
    }
    suspend fun save(item: PersonalItem) = personal.save(item).onSuccess { revision.update { it + 1 } }
    suspend fun delete(id: String) = personal.delete(id).onSuccess { revision.update { it + 1 } }
    override fun onCleared() { refresh?.cancel(); observation.cancel(); personalObservation.cancel() }
}
