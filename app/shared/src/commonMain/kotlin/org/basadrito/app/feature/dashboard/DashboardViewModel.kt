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
import org.basadrito.app.domain.courses.CourseSnapshot
import org.basadrito.app.domain.courses.CourseCacheWarning
import org.basadrito.app.domain.courses.CoursesStorageException
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

    init { loadCourses() }

    fun loadCourses(): Job? {
        if (cleared || state.value.isLoading) return null
        mutableState.update { it.copy(isLoading = true, errorMessage = null) }
        return operationScope.launch {
            try {
                courses.getSnapshot(refresh = false).fold(
                    onSuccess = { it?.let(::showSnapshot) },
                    onFailure = { if (!cleared) mutableState.update { it.copy(cacheWarning = CourseCacheWarning.READ_FAILED) } },
                )
                courses.getSnapshot().fold(
                    onSuccess = { result -> result?.let(::showSnapshot) },
                    onFailure = { error -> if (!cleared) showError(error) },
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

    private fun showError(error: Throwable? = null) {
        mutableState.update { it.copy(errorMessage = if (error is CoursesStorageException) error.message
            else "No se pudieron cargar los cursos. Intenta nuevamente.") }
    }

    private fun showSnapshot(snapshot: CourseSnapshot) {
        if (!cleared) mutableState.update { it.copy(courses = snapshot.courses, source = snapshot.source,
            updatedAtMillis = snapshot.updatedAtMillis, refreshFailed = snapshot.refreshFailed,
            cacheWarning = snapshot.cacheWarning, errorMessage = null) }
    }

    override fun onCleared() {
        cleared = true
        operation?.cancel()
    }
}
