package org.basadrito.app.data.repository

import org.basadrito.app.data.cache.*
import org.basadrito.app.data.diagnostics.*
import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.domain.auth.AccessMode
import org.basadrito.app.domain.courses.*
import kotlin.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CoursesRepositoryImpl(
    private val session: AcademicSession,
    private val cache: CourseCache = NoCourseCache,
    private val diagnostics: StorageDiagnostics = ConsoleStorageDiagnostics,
) : CoursesRepository {
    private class AccountCourses(val connection: AcademicSession.Connection) {
        val feed = MutableStateFlow(CourseFeed())
        val observationRevision = MutableStateFlow(0)
        var refresh: Deferred<Result<CourseSnapshot?>>? = null
        var initialized = false
    }
    private val coordination = Mutex()
    private var accountCourses: AccountCourses? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCourses(): Flow<CourseFeed> = session.activeConnection.flatMapLatest { connection ->
        if (connection == null) flowOf(CourseFeed()) else flow {
            val state = context(connection)
            val owner = CourseOwner(UNJBG_MOODLE_PROVIDER, requireNotNull(connection.moodle).userId)
            val local = state.observationRevision.flatMapLatest {
                cache.observe(owner).map { Result.success(it) }.catch { error ->
                    if (error !is CourseCacheStorageException) throw error
                    diagnostics.report(StorageArea.COURSE_CACHE, StorageAction.READ, error)
                    emit(Result.failure(error))
                }
            }
            emitAll(combine(local, state.feed) { stored, progress ->
                if (!session.isCurrent(connection)) return@combine CourseFeed()
                val remote = progress.snapshot?.takeIf { it.source == CourseSource.REMOTE }
                val cached = stored.getOrNull()
                val snapshot = when {
                    stored.isFailure || remote?.cacheWarning == CourseCacheWarning.WRITE_FAILED -> remote
                    cached == null -> null
                    remote != null && cached.updatedAtMillis < (remote.updatedAtMillis ?: 0) -> remote
                    remote != null && cached.updatedAtMillis == remote.updatedAtMillis -> remote.copy(courses = cached.courses)
                    else -> cached.asSnapshot(progress.snapshot?.refreshFailed == true)
                }
                progress.copy(snapshot = snapshot, storageReadFailed = stored.isFailure)
            })
        }
    }

    private suspend fun context(connection: AcademicSession.Connection): AccountCourses = coordination.withLock {
        session.requireCurrent(connection)
        val state = accountCourses?.takeIf { it.connection === connection }
            ?: AccountCourses(connection).also { accountCourses = it }
        state
    }

    override suspend fun refreshCourses(initial: Boolean): Result<CourseSnapshot?> = coordinatedRefresh(initial)

    private suspend fun coordinatedRefresh(initial: Boolean): Result<CourseSnapshot?> {
        val connection = session.authenticated()
            ?: return Result.failure(IllegalStateException("Inicia sesión para consultar cursos."))
        val state = context(connection)
        val request = coordination.withLock {
            session.requireCurrent(connection)
            state.refresh?.takeIf { !it.isCompleted } ?: run {
                if (initial && state.initialized) return Result.success(state.feed.value.snapshot)
                connection.workScope.async(start = CoroutineStart.LAZY) {
                    state.feed.update { it.copy(isRefreshing = true, failure = null) }
                    try {
                        fetchSnapshot(connection, refresh = true).also { result ->
                            session.requireCurrent(connection)
                            result.onSuccess { snapshot ->
                                state.initialized = snapshot?.source == CourseSource.REMOTE || connection.mode == AccessMode.LOCAL_ONLY
                                state.feed.update { it.copy(snapshot = snapshot) }
                            }
                            result.onFailure { error -> state.feed.update { it.copy(failure =
                                if (error is CoursesStorageException) CourseFailure.STORAGE else CourseFailure.REMOTE) } }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        state.feed.update { it.copy(failure = CourseFailure.REMOTE) }
                        throw error
                    } finally {
                        state.feed.update { it.copy(isRefreshing = false) }
                        state.observationRevision.update { it + 1 }
                    }
                }.also { state.refresh = it; it.start() }
            }
        }
        return request.await()
    }

    override suspend fun getCourses(): Result<List<CourseSummary>> = getSnapshot().map { it?.courses.orEmpty() }

    override suspend fun getSnapshot(refresh: Boolean): Result<CourseSnapshot?> {
        if (refresh) return coordinatedRefresh(initial = false)
        val connection = session.authenticated()
            ?: return Result.failure(IllegalStateException("Inicia sesión para consultar cursos."))
        return fetchSnapshot(connection, refresh = false)
    }

    private suspend fun fetchSnapshot(connection: AcademicSession.Connection, refresh: Boolean): Result<CourseSnapshot?> {
        val account = connection.moodle
            ?: return Result.failure(IllegalStateException("La sesión terminó."))
        val owner = CourseOwner(UNJBG_MOODLE_PROVIDER, account.userId)
        val local = try {
            Result.success(cache.read(owner))
        } catch (error: CourseCacheStorageException) {
            diagnostics.report(StorageArea.COURSE_CACHE, StorageAction.READ, error)
            Result.failure(CoursesStorageException())
        }
        session.requireCurrent(connection)
        if (!refresh || connection.mode == AccessMode.LOCAL_ONLY) return local.map { it?.asSnapshot() }

        val remote = connection.clients.moodle.getCourses(account.token, account.userId)
        session.requireCurrent(connection)
        return remote.fold(
            onSuccess = { courses ->
                val snapshot = CachedCourses(courses.map { CourseSummary(it.id, it.displayLabel, it.shortName,
                    it.startDate.takeIf { date -> date > 0 }, it.endDate.takeIf { date -> date > 0 }) },
                    Clock.System.now().toEpochMilliseconds())
                val saved = try {
                    cache.replace(owner, snapshot) { session.isCurrent(connection) }
                } catch (error: CourseCacheStorageException) {
                    diagnostics.report(StorageArea.COURSE_CACHE, StorageAction.WRITE, error)
                    false
                }
                session.requireCurrent(connection)
                val warning = when {
                    !saved -> CourseCacheWarning.WRITE_FAILED
                    local.isFailure -> CourseCacheWarning.READ_FAILED
                    else -> null
                }
                Result.success(CourseSnapshot(snapshot.courses, CourseSource.REMOTE, snapshot.updatedAtMillis,
                    cacheWarning = warning))
            },
            onFailure = {
                local.fold(
                    onSuccess = { cached ->
                        if (cached != null) Result.success(cached.asSnapshot(refreshFailed = true))
                        else Result.failure(IllegalStateException("No se pudieron cargar los cursos. Intenta nuevamente."))
                    },
                    onFailure = { storageError -> Result.failure(storageError) },
                )
            },
        )
    }

    private fun CachedCourses.asSnapshot(refreshFailed: Boolean = false) =
        CourseSnapshot(courses, CourseSource.CACHE, updatedAtMillis, refreshFailed)
}
