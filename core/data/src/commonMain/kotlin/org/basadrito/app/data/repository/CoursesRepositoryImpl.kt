package org.basadrito.app.data.repository

import org.basadrito.app.data.cache.*
import org.basadrito.app.data.diagnostics.*
import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.domain.auth.AccessMode
import org.basadrito.app.domain.courses.*
import kotlin.time.Clock

class CoursesRepositoryImpl(
    private val session: AcademicSession,
    private val cache: CourseCache = NoCourseCache,
    private val diagnostics: StorageDiagnostics = ConsoleStorageDiagnostics,
) : CoursesRepository {
    override suspend fun getCourses(): Result<List<CourseSummary>> = getSnapshot().map { it?.courses.orEmpty() }

    override suspend fun getSnapshot(refresh: Boolean): Result<CourseSnapshot?> {
        val connection = session.authenticated()
            ?: return Result.failure(IllegalStateException("Inicia sesión para consultar cursos."))
        val account = connection.moodle
            ?: return Result.failure(IllegalStateException("La sesión terminó."))
        val owner = CourseOwner(UNJBG_MOODLE_PROVIDER, account.userId)
        val local = try {
            Result.success(cache.read(owner))
        } catch (error: CourseCacheStorageException) {
            diagnostics.report(StorageOperation.READ_COURSES, error)
            Result.failure(CoursesStorageException())
        }
        session.requireCurrent(connection)
        if (!refresh || connection.mode == AccessMode.LOCAL_ONLY) return local.map { it?.asSnapshot() }

        val remote = connection.clients.moodle.getCourses(account.token, account.userId)
        session.requireCurrent(connection)
        return remote.fold(
            onSuccess = { courses ->
                val snapshot = CachedCourses(courses.map { CourseSummary(it.id, it.displayLabel, it.shortName) },
                    Clock.System.now().toEpochMilliseconds())
                val saved = try {
                    cache.replace(owner, snapshot) { session.isCurrent(connection) }
                } catch (error: CourseCacheStorageException) {
                    diagnostics.report(StorageOperation.WRITE_COURSES, error)
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
