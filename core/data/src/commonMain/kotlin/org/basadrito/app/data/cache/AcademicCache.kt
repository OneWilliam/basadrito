package org.basadrito.app.data.cache

import kotlinx.coroutines.flow.Flow
import org.basadrito.app.domain.academic.AcademicSnapshot

interface AcademicCache {
    fun observe(owner: CourseOwner): Flow<AcademicSnapshot?>
    suspend fun read(owner: CourseOwner): AcademicSnapshot?
    suspend fun replace(owner: CourseOwner, snapshot: AcademicSnapshot, isCurrent: () -> Boolean): Boolean
}
