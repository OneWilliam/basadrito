package org.basadrito.app.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.basadrito.app.data.cache.*
import org.basadrito.app.data.diagnostics.*
import org.basadrito.app.data.http.apiCall
import org.basadrito.app.data.intranet.dto.AcademicRecord
import org.basadrito.app.data.intranet.dto.MallaCourse
import org.basadrito.app.data.intranet.format.toDoubleFlexible
import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.domain.academic.*
import org.basadrito.app.domain.auth.AccessMode
import org.basadrito.app.domain.courses.CoursesRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class AcademicRepositoryImpl(private val session: AcademicSession, private val courses: CoursesRepository,
    private val cache: AcademicCache, private val diagnostics: StorageDiagnostics = ConsoleStorageDiagnostics) : AcademicRepository {
    private class Account(val connection: AcademicSession.Connection) {
        val state = MutableStateFlow(AcademicFeed())
        val revision = MutableStateFlow(0)
        var request: Deferred<Unit>? = null
        var initialized = false
    }
    private val mutex = Mutex()
    private var account: Account? = null
    private suspend fun account(connection: AcademicSession.Connection) = mutex.withLock {
        session.requireCurrent(connection)
        account?.takeIf { it.connection === connection }
            ?: Account(connection).also { account = it }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<AcademicFeed> = session.activeConnection.flatMapLatest { connection ->
        if (connection == null) flowOf(AcademicFeed()) else flow {
            val state = account(connection)
            val owner = CourseOwner(UNJBG_MOODLE_PROVIDER, requireNotNull(connection.moodle).userId)
            val local = state.revision.flatMapLatest {
                cache.observe(owner).map { Result.success(it) }.catch { error ->
                    if (error !is CourseCacheStorageException) throw error
                    diagnostics.report(StorageArea.ACADEMIC_CACHE, StorageAction.READ, error)
                    emit(Result.failure(error))
                }
            }
            emitAll(combine(local, state.state) { saved, progress ->
                if (!session.isCurrent(connection)) AcademicFeed()
                else progress.copy(snapshot = newest(saved.getOrNull() ?: AcademicSnapshot(), progress.snapshot),
                    storageFailed = progress.storageFailed || saved.isFailure)
            })
        }
    }

    override suspend fun refresh(initial: Boolean) {
        val connection = session.authenticated() ?: return
        val state = account(connection)
        val work = mutex.withLock {
            session.requireCurrent(connection)
            state.request?.takeIf { !it.isCompleted } ?: run {
                if (initial && state.initialized) return
                connection.workScope.async(start = CoroutineStart.LAZY) {
                    state.state.update { it.copy(isRefreshing = true, failedSections = emptySet(), storageFailed = false) }
                    try { update(state) }
                    finally {
                        state.state.update { it.copy(isRefreshing = false) }
                        state.revision.update { it + 1 }
                    }
                }.also { state.request = it; it.start() }
            }
        }
        work.await()
    }

    private suspend fun update(state: Account) = coroutineScope {
        val connection = state.connection
        val identity = requireNotNull(connection.moodle)
        val owner = CourseOwner(UNJBG_MOODLE_PROVIDER, identity.userId)
        var storageFailed = false
        val saved = try { cache.read(owner) ?: AcademicSnapshot() }
        catch (error: CourseCacheStorageException) {
            diagnostics.report(StorageArea.ACADEMIC_CACHE, StorageAction.READ, error)
            storageFailed = true
            state.state.value.snapshot
        }
        session.requireCurrent(connection)
        if (connection.mode == AccessMode.LOCAL_ONLY) {
            state.state.update { it.copy(snapshot = saved, storageFailed = storageFailed) }
            state.initialized = true
            return@coroutineScope
        }
        val courseRequest = async { courses.refreshCourses(initial = true) }
        val creditRequest = async { apiCall {
            val curriculum = connection.clients.intranet.getMalla().getOrThrow()
            val cycles = curriculum.map { it.cycle }.filter { it > 0 }
            require(cycles.isNotEmpty()) { "La malla no contiene ciclos válidos." }
            calculateCredits(curriculum, connection.clients.intranet.getAcademicProgress(cycles.min()..cycles.max()).getOrThrow())
        } }
        // Intranet supplies local civil dates. The institution uses UTC-05, without DST.
        val today = (Clock.System.now() - 5.hours).toString().take(10)
        val scheduleRequest = async { connection.clients.intranet.getWeekSchedule(today).map { rows -> rows.map {
            AcademicClass(it.course, it.detail, it.day, it.startTime, it.endTime, it.startIso, it.endIso) } } }
        val attendanceRequest = async { connection.clients.intranet.getAttendance().map { rows -> rows.map {
            CourseAttendance(it.course, it.attended, it.dictated) } } }
        val ids = courseRequest.await().map { it?.courses.orEmpty().map { course -> course.id } }
        val teacherRequest = async { ids.fold(
            onSuccess = { connection.clients.moodle.getCourseTeachers(identity.token, it) },
            onFailure = { Result.failure(it) }) }
        val taskRequest = async { apiCall {
            val courseIds = ids.getOrThrow()
            val assignments = if (courseIds.isEmpty()) emptyList() else
                connection.clients.moodle.getAssignments(identity.token, courseIds).getOrThrow()
            val permits = Semaphore(4)
            assignments.map { assignment -> async { permits.withPermit {
                val result = connection.clients.moodle.getSubmissionStatus(identity.token, assignment.id)
                result.fold(onSuccess = { response ->
                    val attempt = response.lastAttempt
                    val status = when (attempt?.submission?.status) {
                        "new" -> SubmissionState.NEW
                        "draft" -> SubmissionState.DRAFT
                        "submitted" -> SubmissionState.SUBMITTED
                        "reopened" -> SubmissionState.REOPENED
                        else -> SubmissionState.UNKNOWN
                    }
                    AcademicTask(assignment.id, assignment.course, assignment.name,
                        assignment.dueDate.takeIf { it > 0 }, assignment.allowSubmissionsFromDate.takeIf { it > 0 }, status, attempt?.graded)
                }, onFailure = {
                    AcademicTask(assignment.id, assignment.course, assignment.name,
                        assignment.dueDate.takeIf { it > 0 }, assignment.allowSubmissionsFromDate.takeIf { it > 0 },
                        SubmissionState.UNKNOWN, null)
                })
            } } }.awaitAll()
        } }
        val credits = creditRequest.await()
        val schedule = scheduleRequest.await()
        val attendance = attendanceRequest.await()
        val teachers = teacherRequest.await()
        val tasks = taskRequest.await()
        session.requireCurrent(connection)
        val results = mapOf(AcademicSection.CREDITS to credits.isSuccess, AcademicSection.SCHEDULE to schedule.isSuccess,
            AcademicSection.ATTENDANCE to attendance.isSuccess, AcademicSection.TEACHERS to teachers.isSuccess,
            AcademicSection.TASKS to tasks.isSuccess)
        val now = Clock.System.now().toEpochMilliseconds()
        val updated = saved.copy(credits = credits.getOrNull() ?: saved.credits, classes = schedule.getOrNull() ?: saved.classes,
            attendance = attendance.getOrNull() ?: saved.attendance, teachers = teachers.getOrNull() ?: saved.teachers,
            tasks = tasks.getOrNull() ?: saved.tasks, updatedAtMillis = saved.updatedAtMillis +
                results.filterValues { it }.mapValues { now })
        val failed = results.filterValues { !it }.keys +
            if (tasks.getOrNull()?.any { it.submission == SubmissionState.UNKNOWN } == true) setOf(AcademicSection.TASKS) else emptySet()
        if (results.any { it.value }) {
            val persisted = try { cache.replace(owner, updated) { session.isCurrent(connection) } }
            catch (error: CourseCacheStorageException) {
                diagnostics.report(StorageArea.ACADEMIC_CACHE, StorageAction.WRITE, error)
                false
            }
            storageFailed = storageFailed || !persisted
        }
        session.requireCurrent(connection)
        state.state.update { it.copy(snapshot = updated, failedSections = failed, storageFailed = storageFailed) }
        state.initialized = results.any { it.value }
    }
}

/** Only explicit approval counts. Repeated attempts never add the same curriculum credits twice. */
internal fun calculateCredits(curriculum: List<MallaCourse>, records: List<AcademicRecord>): CreditProgress {
    require(curriculum.isNotEmpty()) { "La malla no está disponible." }
    val plan = curriculum.groupBy { it.code.trim() }.mapValues { (_, rows) ->
        val values = rows.map { it.credits.toDoubleFlexible() }.distinct()
        require(values.size == 1 && values.single() != null && values.single()!! > 0) { "Créditos de malla no válidos." }
        values.single()!!
    }
    require(plan.keys.none { it.isBlank() }) { "La malla no identifica los cursos." }
    require(records.none { it.approved == null }) { "El estado académico no está disponible." }
    val approved = records.filter { it.approved == true }.map { it.courseCode.trim() }.toSet()
    return CreditProgress(plan.filterKeys { it in approved }.values.sum(), plan.values.sum())
}

/** Merge each independently refreshed section; unavailable sources never erase older successful data. */
private fun newest(local: AcademicSnapshot, remote: AcademicSnapshot): AcademicSnapshot {
    fun use(section: AcademicSection) = (remote.updatedAtMillis[section] ?: -1) >= (local.updatedAtMillis[section] ?: 0)
    return AcademicSnapshot(
        if (use(AcademicSection.CREDITS)) remote.credits else local.credits,
        if (use(AcademicSection.SCHEDULE)) remote.classes else local.classes,
        if (use(AcademicSection.ATTENDANCE)) remote.attendance else local.attendance,
        if (use(AcademicSection.TEACHERS)) remote.teachers else local.teachers,
        if (use(AcademicSection.TASKS)) remote.tasks else local.tasks,
        (local.updatedAtMillis.keys + remote.updatedAtMillis.keys).associateWith { section ->
            maxOf(local.updatedAtMillis[section] ?: 0, remote.updatedAtMillis[section] ?: 0) },
    )
}
