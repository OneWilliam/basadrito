package org.basadrito.app.database

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.basadrito.app.data.cache.CachedCourses
import org.basadrito.app.data.cache.CourseCache
import org.basadrito.app.data.cache.CourseOwner
import org.basadrito.app.data.cache.CourseCacheStorageException
import androidx.sqlite.SQLiteException
import kotlinx.coroutines.flow.*
import org.basadrito.app.domain.courses.CourseSummary

class RoomCourseCache(private val database: AcademicDatabase) : CourseCache {
    override fun observe(owner: CourseOwner): Flow<CachedCourses?> = database.courseDao()
        .observe(owner.provider, owner.userId).map { rows ->
            rows.firstOrNull()?.let { first ->
                CachedCourses(rows.mapNotNull { row -> row.courseId?.let { id ->
                    CourseSummary(id, requireNotNull(row.name), requireNotNull(row.shortName), row.startsAtEpochSeconds, row.endsAtEpochSeconds)
                } }, first.updatedAtMillis)
            }
        }.distinctUntilChanged().catch { error ->
            if (error is SQLiteException) throw CourseCacheStorageException(error)
            throw error
        }

    private val mutex = Mutex()
    override suspend fun read(owner: CourseOwner): CachedCourses? = storageOperation { database.courseDao().read(owner) }
    override suspend fun replace(owner: CourseOwner, snapshot: CachedCourses, isCurrent: () -> Boolean): Boolean =
        storageOperation { database.courseDao().replace(owner, snapshot, isCurrent) }
    override suspend fun delete(owner: CourseOwner) = storageOperation { database.courseDao().delete(owner) }

    private suspend fun <T> storageOperation(block: suspend () -> T): T = mutex.withLock {
        try {
            block()
        } catch (error: SQLiteException) {
            throw CourseCacheStorageException(error)
        }
    }
    fun close() = database.close()
}
