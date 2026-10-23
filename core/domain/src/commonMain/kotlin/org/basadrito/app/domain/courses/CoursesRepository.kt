package org.basadrito.app.domain.courses

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class CourseSummary(val id: Int, val name: String, val shortName: String,
    val startsAtEpochSeconds: Long? = null, val endsAtEpochSeconds: Long? = null)

enum class CourseSource { REMOTE, CACHE }

enum class CourseCacheWarning { READ_FAILED, WRITE_FAILED }

class CoursesStorageException : IllegalStateException("No se pudieron abrir los cursos guardados. Intenta nuevamente.")

data class CourseSnapshot(
    val courses: List<CourseSummary>,
    val source: CourseSource,
    val updatedAtMillis: Long? = null,
    val refreshFailed: Boolean = false,
    val cacheWarning: CourseCacheWarning? = null,
)

interface CoursesRepository {
    /** Account-scoped local changes and shared refresh progress. */
    fun observeCourses(): Flow<CourseFeed> = emptyFlow()

    suspend fun refreshCourses(initial: Boolean = false): Result<CourseSnapshot?> = getSnapshot()

    suspend fun getCourses(): Result<List<CourseSummary>>

    suspend fun getSnapshot(refresh: Boolean = true): Result<CourseSnapshot?> =
        if (!refresh) Result.success(null)
        else getCourses().map { CourseSnapshot(it, CourseSource.REMOTE) }
}

data class CourseFeed(
    val snapshot: CourseSnapshot? = null,
    val isRefreshing: Boolean = false,
    val storageReadFailed: Boolean = false,
    val failure: CourseFailure? = null,
)

enum class CourseFailure { STORAGE, REMOTE }
