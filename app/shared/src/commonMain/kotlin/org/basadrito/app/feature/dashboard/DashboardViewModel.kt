package org.basadrito.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.basadrito.app.domain.courses.CoursesRepository
import kotlin.coroutines.cancellation.CancellationException

class DashboardViewModel(
    private val courses: CoursesRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val operationScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(DashboardUiState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var cleared = false

    fun loadCourses(): Job? {
        if (cleared || state.value.isLoading) return null
        mutableState.update { it.copy(isLoading = true, errorMessage = null) }
        return operationScope.launch {
            try {
                courses.getCourses().fold(
                    onSuccess = { result -> if (!cleared) mutableState.update { it.copy(courses = result) } },
                    onFailure = { if (!cleared) showError() },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!cleared) showError()
            } finally {
                if (!cleared) mutableState.update { it.copy(isLoading = false) }
            }
        }.also { operation = it }
    }

    private fun showError() {
        mutableState.update { it.copy(errorMessage = "No se pudieron cargar los cursos. Intenta nuevamente.") }
    }

    override fun onCleared() {
        cleared = true
        operation?.cancel()
    }
}
