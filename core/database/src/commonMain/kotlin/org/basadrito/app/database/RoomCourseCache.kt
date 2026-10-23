package org.basadrito.app.database

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.basadrito.app.data.cache.CachedCourses
import org.basadrito.app.data.cache.CourseCache
import org.basadrito.app.data.cache.CourseOwner
import org.basadrito.app.data.cache.CourseCacheStorageException
import androidx.sqlite.SQLiteException

class RoomCourseCache(private val database: AcademicDatabase) : CourseCache {
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
