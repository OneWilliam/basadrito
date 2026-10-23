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
import org.basadrito.app.domain.courses.CourseFailure
import kotlin.coroutines.cancellation.CancellationException
import org.basadrito.app.domain.academic.*
import org.basadrito.app.domain.calendar.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val courses: CoursesRepository,
    scope: CoroutineScope? = null,
    private val academic: AcademicRepository = NoAcademicRepository,
    private val personal: PersonalAgendaRepository = NoPersonalAgendaRepository,
) : ViewModel() {
    private val operationScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(DashboardUiState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var cleared = false
    private val mutableAcademicState = MutableStateFlow(AcademicFeed())
    val academicState = mutableAcademicState.asStateFlow()
    private val own = MutableStateFlow(emptyList<PersonalItem>())
    val personalItems = own.asStateFlow()
    private val ownError = MutableStateFlow<String?>(null)
    val personalError = ownError.asStateFlow()
    private val ownRevision = MutableStateFlow(0)
    private val ownObservation = operationScope.launch {
        ownRevision.flatMapLatest { personal.observe().catch { ownError.value = "No se pudieron abrir tus tareas." } }
            .collect { own.value = it; ownError.value = null }
    }
    suspend fun savePersonal(item: PersonalItem) = personal.save(item).onSuccess { ownRevision.update { it + 1 } }
    suspend fun deletePersonal(id: String) = personal.delete(id).onSuccess { ownRevision.update { it + 1 } }
    private var academicRefresh: Job? = null
    private val academicObservation = operationScope.launch {
        try { academic.observe().collect { if (!cleared) mutableAcademicState.value = it } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { academicError() }
    }
    private val observation: Job = operationScope.launch {
        try {
            courses.observeCourses().collect { feed ->
                if (!cleared) {
                    val snapshot = feed.snapshot
                    mutableState.update { it.copy(courses = snapshot?.courses.orEmpty(), source = snapshot?.source,
                        updatedAtMillis = snapshot?.updatedAtMillis, refreshFailed = snapshot?.refreshFailed ?: false,
                        isLoading = feed.isRefreshing,
                        cacheWarning = if (feed.storageReadFailed) CourseCacheWarning.READ_FAILED else snapshot?.cacheWarning,
                        errorMessage = when (feed.failure) {
                            CourseFailure.STORAGE -> CoursesStorageException().message
                            CourseFailure.REMOTE -> "No se pudieron cargar los cursos. Intenta nuevamente."
                            null -> null
                        }) }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (!cleared) showError()
        }
    }

    init { loadCourses(initial = true) }

    fun loadCourses(initial: Boolean = false): Job? {
        if (cleared || operation?.isActive == true || state.value.isLoading) return null
        mutableState.update { it.copy(isLoading = true, errorMessage = null) }
        ownRevision.update { it + 1 }
        academicRefresh = operationScope.launch {
            try { academic.refresh(initial) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { academicError() }
        }
        return operationScope.launch {
            try {
                courses.getSnapshot(refresh = false).fold(
                    onSuccess = { it?.let(::showSnapshot) },
                    onFailure = { if (!cleared) mutableState.update { it.copy(cacheWarning = CourseCacheWarning.READ_FAILED) } },
                )
                courses.refreshCourses(initial).fold(
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

    private fun academicError() {
        if (!cleared) mutableAcademicState.update { it.copy(isRefreshing = false, failedSections = AcademicSection.entries.toSet()) }
    }

    private fun showSnapshot(snapshot: CourseSnapshot) {
        if (!cleared) mutableState.update { it.copy(courses = snapshot.courses, source = snapshot.source,
            updatedAtMillis = snapshot.updatedAtMillis, refreshFailed = snapshot.refreshFailed,
            cacheWarning = snapshot.cacheWarning, errorMessage = null) }
    }

    override fun onCleared() {
        cleared = true
        operation?.cancel()
        observation.cancel()
        academicObservation.cancel()
        academicRefresh?.cancel()
        ownObservation.cancel()
    }
}
