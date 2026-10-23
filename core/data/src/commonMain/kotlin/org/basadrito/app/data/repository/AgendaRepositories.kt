package org.basadrito.app.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.basadrito.app.data.cache.*
import org.basadrito.app.data.diagnostics.*
import org.basadrito.app.data.http.apiCall
import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.domain.academic.*
import org.basadrito.app.domain.auth.AccessMode
import org.basadrito.app.domain.calendar.*
import kotlin.time.Clock
import kotlin.uuid.Uuid

class PersonalAgendaRepositoryImpl(private val session: AcademicSession, private val cache: AgendaCache,
    private val diagnostics: StorageDiagnostics = ConsoleStorageDiagnostics) : PersonalAgendaRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<List<PersonalItem>> = session.activeConnection.flatMapLatest { connection ->
        if (connection == null) flowOf(emptyList()) else cache.observePersonal(owner(connection)).map {
            if (session.isCurrent(connection)) it else emptyList()
        }.catch { error ->
            if (error is CourseCacheStorageException) diagnostics.report(StorageArea.ACADEMIC_CACHE, StorageAction.READ, error)
            throw error
        }
    }
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    override suspend fun save(item: PersonalItem): Result<Unit> = write(StorageAction.WRITE) { connection ->
        item.validate()
        cache.savePersonal(owner(connection), item.copy(id = item.id.ifBlank { Uuid.random().toString() }, title = item.title.trim())) {
            session.isCurrent(connection)
        }
    }
    override suspend fun delete(id: String): Result<Unit> = write(StorageAction.CLEAR) { connection ->
        require(id.isNotBlank())
        cache.deletePersonal(owner(connection), id) { session.isCurrent(connection) }
    }
    private suspend fun write(action: StorageAction, block: suspend (AcademicSession.Connection) -> Unit): Result<Unit> = apiCall {
        val connection = session.authenticated() ?: error("Inicia sesión para usar tu agenda.")
        try { block(connection); session.requireCurrent(connection) }
        catch (error: CourseCacheStorageException) {
            diagnostics.report(StorageArea.ACADEMIC_CACHE, action, error)
            error("No se pudo guardar el cambio en tu agenda. Intenta nuevamente.")
        }
    }
    private fun owner(connection: AcademicSession.Connection) = CourseOwner(UNJBG_MOODLE_PROVIDER, requireNotNull(connection.moodle).userId)
}

class CalendarRepositoryImpl(private val session: AcademicSession, private val academic: AcademicRepository,
    private val personal: PersonalAgendaRepository, private val cache: AgendaCache,
    private val diagnostics: StorageDiagnostics = ConsoleStorageDiagnostics) : CalendarRepository {
    private data class Status(val loading: Boolean = false, val failed: Boolean = false,
        val storageFailed: Boolean = false, val remote: CalendarMonthData? = null)
    private class MonthState {
        val status = MutableStateFlow(Status())
        val revision = MutableStateFlow(0)
        var work: Deferred<Unit>? = null
    }
    private val mutex = Mutex()
    private var current: AcademicSession.Connection? = null
    private val months = mutableMapOf<String, MonthState>()
    private suspend fun state(connection: AcademicSession.Connection, month: CalendarMonth): MonthState = mutex.withLock {
        session.requireCurrent(connection)
        if (current !== connection) { months.clear(); current = connection }
        months.getOrPut(month.key) { MonthState() }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(month: CalendarMonth): Flow<CalendarFeed> = session.activeConnection.flatMapLatest { connection ->
        if (connection == null) flowOf(CalendarFeed()) else flow {
            val state = state(connection, month)
            val owner = CourseOwner(UNJBG_MOODLE_PROVIDER, requireNotNull(connection.moodle).userId)
            val stored = state.revision.flatMapLatest { cache.observeMonth(owner, month).map { Result.success(it) }.catch { error ->
                if (error !is CourseCacheStorageException) throw error
                diagnostics.report(StorageArea.ACADEMIC_CACHE, StorageAction.READ, error)
                emit(Result.failure(error))
            } }
            emitAll(combine(stored, academic.observe(), personal.observe(), state.status) { saved, academicFeed, personalItems, status ->
                if (!session.isCurrent(connection)) return@combine CalendarFeed()
                val local = saved.getOrNull()
                val schedule = status.remote?.takeIf { it.updatedAtMillis >= (local?.updatedAtMillis ?: 0) } ?: local
                val classes = schedule?.classes.orEmpty().distinctBy { listOf(it.course, it.startIso, it.endIso, it.detail) }.map {
                    CalendarEvent("class:${it.startIso}:${it.course.length}:${it.course}:${it.detail}", it.course,
                        it.detail.replace(Regex("<[^>]*>"), " ").trim(), it.startIso.take(10), it.startTime, CalendarOrigin.CLASS)
                }
                val tasks = academicFeed.snapshot.tasks.orEmpty().mapNotNull { task -> task.dueAtEpochSeconds?.let { due ->
                    CalendarEvent("moodle:${task.id}", task.name, "Entrega de Moodle", institutionDate(due), institutionTime(due),
                        CalendarOrigin.MOODLE, task.pending?.not())
                } }
                val own = personalItems.mapNotNull { item -> item.date?.let { date -> CalendarEvent("personal:${item.id}",
                    item.title, item.description, date, item.time, when (item.kind) {
                        PersonalKind.TASK -> CalendarOrigin.PERSONAL_TASK
                        PersonalKind.EVENT -> CalendarOrigin.PERSONAL_EVENT
                        PersonalKind.EXAM -> CalendarOrigin.PERSONAL_EXAM
                    }, if (item.kind == PersonalKind.TASK) item.completed else null) } }
                CalendarFeed((classes + tasks + own).filter { it.date.startsWith(month.key + "-") }
                    .sortedWith(compareBy<CalendarEvent> { it.date }.thenBy { it.time ?: "" }.thenBy { it.id }),
                    status.loading || academicFeed.isRefreshing, status.failed || AcademicSection.TASKS in academicFeed.failedSections,
                    status.storageFailed || saved.isFailure || academicFeed.storageFailed, schedule != null, academicFeed.snapshot.tasks != null)
            })
        }
    }
    override suspend fun refresh(month: CalendarMonth, refreshTasks: Boolean) {
        val connection = session.authenticated() ?: return
        val state = state(connection, month)
        val work = mutex.withLock {
            state.work?.takeIf { !it.isCompleted } ?: connection.workScope.async(start = CoroutineStart.LAZY) {
                state.status.update { it.copy(loading = true, failed = false, storageFailed = false) }
                try {
                    if (connection.mode == AccessMode.LOCAL_ONLY) return@async
                    val tasks = async { academic.refresh(initial = !refreshTasks) }
                    val result = connection.clients.intranet.getSchedule(month.firstDate, month.shift(1).firstDate).mapCatching { rows ->
                        require(rows.all { validCivilDate(it.startIso.take(10)) && it.startTime.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")) }) {
                            "El horario incluye fechas u horas no válidas."
                        }
                        CalendarMonthData(rows.map { AcademicClass(it.course, it.detail, it.day, it.startTime, it.endTime, it.startIso, it.endIso) },
                            Clock.System.now().toEpochMilliseconds())
                    }
                    session.requireCurrent(connection)
                    result.fold(onSuccess = { data ->
                        val saved = try { cache.replaceMonth(CourseOwner(UNJBG_MOODLE_PROVIDER, requireNotNull(connection.moodle).userId), month, data) {
                            session.isCurrent(connection) } }
                        catch (error: CourseCacheStorageException) {
                            diagnostics.report(StorageArea.ACADEMIC_CACHE, StorageAction.WRITE, error)
                            false
                        }
                        session.requireCurrent(connection)
                        state.status.update { it.copy(remote = data, storageFailed = !saved) }
                    }, onFailure = { state.status.update { it.copy(failed = true) } })
                    tasks.await()
                } finally { state.status.update { it.copy(loading = false) }; state.revision.update { it + 1 } }
            }.also { state.work = it; it.start() }
        }
        work.await()
    }
}
