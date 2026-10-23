package org.basadrito.app.data.cache

import kotlinx.coroutines.flow.Flow
import org.basadrito.app.domain.calendar.*

interface AgendaCache {
    fun observeMonth(owner: CourseOwner, month: CalendarMonth): Flow<CalendarMonthData?>
    suspend fun replaceMonth(owner: CourseOwner, month: CalendarMonth, data: CalendarMonthData, isCurrent: () -> Boolean): Boolean
    fun observePersonal(owner: CourseOwner): Flow<List<PersonalItem>>
    suspend fun savePersonal(owner: CourseOwner, item: PersonalItem, isCurrent: () -> Boolean)
    suspend fun deletePersonal(owner: CourseOwner, id: String, isCurrent: () -> Boolean)
}
