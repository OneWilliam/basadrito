package org.basadrito.app.data.cache

import org.basadrito.app.domain.courses.CourseSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Provider + external user ID, never an unverified username or a global "current user" row. */
data class CourseOwner(val provider: String, val userId: Int) {
    init { require(provider.isNotBlank() && userId > 0) }
}

data class CachedCourses(val courses: List<CourseSummary>, val updatedAtMillis: Long)

/** Recoverable storage failure. A null read means absence, never an unsuccessful query. */
class CourseCacheStorageException(cause: Exception) : Exception("No se pudo acceder a la caché de cursos.", cause)

/** Storage failures use CourseCacheStorageException; cancellation and programming errors propagate. */
interface CourseCache {
    fun observe(owner: CourseOwner): Flow<CachedCourses?> = flow { emit(read(owner)) }
    suspend fun read(owner: CourseOwner): CachedCourses?
    suspend fun replace(owner: CourseOwner, snapshot: CachedCourses, isCurrent: () -> Boolean): Boolean
    suspend fun delete(owner: CourseOwner)
}

object NoCourseCache : CourseCache {
    override suspend fun read(owner: CourseOwner): CachedCourses? = null
    override suspend fun replace(owner: CourseOwner, snapshot: CachedCourses, isCurrent: () -> Boolean) = false
    override suspend fun delete(owner: CourseOwner) = Unit
}

const val UNJBG_MOODLE_PROVIDER = "moodle:unjbg"
