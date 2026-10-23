package org.basadrito.app.database

import androidx.sqlite.SQLiteException
import kotlinx.coroutines.flow.*
import org.basadrito.app.data.cache.*
import org.basadrito.app.domain.academic.AcademicSnapshot

class RoomAcademicCache(private val database: AcademicDatabase) : AcademicCache {
    override fun observe(owner: CourseOwner): Flow<AcademicSnapshot?> = database.academicOverviewDao()
        .observe(owner.provider, owner.userId).map { it?.toSnapshot() }.distinctUntilChanged().catch {
            if (it is SQLiteException) throw CourseCacheStorageException(it)
            throw it
        }
    override suspend fun read(owner: CourseOwner): AcademicSnapshot? = storage {
        database.academicOverviewDao().read(owner.provider, owner.userId)?.toSnapshot()
    }
    override suspend fun replace(owner: CourseOwner, snapshot: AcademicSnapshot, isCurrent: () -> Boolean): Boolean =
        storage { database.academicOverviewDao().replace(owner, snapshot, isCurrent) }
    private suspend fun <T> storage(block: suspend () -> T): T = try { block() }
        catch (error: SQLiteException) { throw CourseCacheStorageException(error) }
}
