package org.basadrito.app.domain.courses

data class CourseSummary(val id: Int, val name: String, val shortName: String)

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
    suspend fun getCourses(): Result<List<CourseSummary>>

    suspend fun getSnapshot(refresh: Boolean = true): Result<CourseSnapshot?> =
        if (!refresh) Result.success(null)
        else getCourses().map { CourseSnapshot(it, CourseSource.REMOTE) }
}
