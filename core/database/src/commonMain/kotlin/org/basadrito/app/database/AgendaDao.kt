package org.basadrito.app.database

import androidx.room.*
import androidx.sqlite.SQLiteException
import kotlinx.coroutines.flow.*
import org.basadrito.app.data.cache.*
import org.basadrito.app.domain.academic.AcademicClass
import org.basadrito.app.domain.calendar.*
import kotlin.coroutines.cancellation.CancellationException

@Entity(tableName = "calendar_months", indices = [Index(value = ["provider", "ownerId", "month"], unique = true)])
data class CalendarMonthEntity(@PrimaryKey val bucket: String, val provider: String, val ownerId: Int, val month: String, val updatedAtMillis: Long)
@Entity(tableName = "calendar_classes", primaryKeys = ["bucket", "position"],
    foreignKeys = [ForeignKey(entity = CalendarMonthEntity::class, parentColumns = ["bucket"], childColumns = ["bucket"], onDelete = ForeignKey.CASCADE)])
data class CalendarClassEntity(val bucket: String, val position: Int, val course: String, val detail: String,
    val day: String, val startTime: String, val endTime: String, val startIso: String, val endIso: String)
@Entity(tableName = "personal_agenda", primaryKeys = ["provider", "ownerId", "id"])
data class PersonalItemEntity(val provider: String, val ownerId: Int, val id: String, val title: String,
    val description: String, val kind: String, val date: String?, val time: String?, val completed: Boolean)
data class CalendarGraph(@Embedded val month: CalendarMonthEntity,
    @Relation(parentColumn = "bucket", entityColumn = "bucket") val classes: List<CalendarClassEntity>)

@Dao
abstract class AgendaDao {
    @Transaction @Query("SELECT * FROM calendar_months WHERE provider = :provider AND ownerId = :ownerId AND month = :month")
    abstract fun observeMonth(provider: String, ownerId: Int, month: String): Flow<CalendarGraph?>
    @Query("SELECT * FROM personal_agenda WHERE provider = :provider AND ownerId = :ownerId ORDER BY date, time, id")
    abstract fun observePersonal(provider: String, ownerId: Int): Flow<List<PersonalItemEntity>>
    @Upsert abstract suspend fun putMonth(row: CalendarMonthEntity)
    @Insert abstract suspend fun putClasses(rows: List<CalendarClassEntity>)
    @Upsert abstract suspend fun putPersonal(row: PersonalItemEntity)
    @Query("DELETE FROM calendar_classes WHERE bucket = :bucket") abstract suspend fun clearClasses(bucket: String)
    @Query("DELETE FROM personal_agenda WHERE provider = :provider AND ownerId = :ownerId AND id = :id")
    abstract suspend fun deletePersonal(provider: String, ownerId: Int, id: String)
    @Transaction open suspend fun replace(owner: CourseOwner, month: CalendarMonth, data: CalendarMonthData, current: () -> Boolean): Boolean {
        if (!current()) return false
        val bucket = owner.academicKey() + ":" + month.key
        putMonth(CalendarMonthEntity(bucket, owner.provider, owner.userId, month.key, data.updatedAtMillis))
        clearClasses(bucket)
        putClasses(data.classes.mapIndexed { index, c -> CalendarClassEntity(bucket, index, c.course, c.detail, c.day,
            c.startTime, c.endTime, c.startIso, c.endIso) })
        if (!current()) throw CancellationException("La cuenta cambió.")
        return true
    }
    @Transaction open suspend fun save(owner: CourseOwner, item: PersonalItem, current: () -> Boolean) {
        if (!current()) throw CancellationException("La cuenta cambió.")
        putPersonal(PersonalItemEntity(owner.provider, owner.userId, item.id, item.title, item.description, item.kind.name,
            item.date, item.time, item.completed))
        if (!current()) throw CancellationException("La cuenta cambió.")
    }
    @Transaction open suspend fun delete(owner: CourseOwner, id: String, current: () -> Boolean) {
        if (!current()) throw CancellationException("La cuenta cambió.")
        deletePersonal(owner.provider, owner.userId, id)
        if (!current()) throw CancellationException("La cuenta cambió.")
    }
}

class RoomAgendaCache(private val database: AcademicDatabase) : AgendaCache {
    private val dao get() = database.agendaDao()
    override fun observeMonth(owner: CourseOwner, month: CalendarMonth): Flow<CalendarMonthData?> = dao
        .observeMonth(owner.provider, owner.userId, month.key).map { graph -> graph?.let {
            CalendarMonthData(it.classes.sortedBy { row -> row.position }.map { row -> AcademicClass(row.course, row.detail,
                row.day, row.startTime, row.endTime, row.startIso, row.endIso) }, it.month.updatedAtMillis)
        } }.distinctUntilChanged().storageErrors()
    override suspend fun replaceMonth(owner: CourseOwner, month: CalendarMonth, data: CalendarMonthData, isCurrent: () -> Boolean) =
        storage { dao.replace(owner, month, data, isCurrent) }
    override fun observePersonal(owner: CourseOwner): Flow<List<PersonalItem>> = dao.observePersonal(owner.provider, owner.userId)
        .map { rows -> rows.map { PersonalItem(it.id, it.title, it.description, PersonalKind.valueOf(it.kind), it.date, it.time, it.completed) } }
        .distinctUntilChanged().storageErrors()
    override suspend fun savePersonal(owner: CourseOwner, item: PersonalItem, isCurrent: () -> Boolean) = storage { dao.save(owner, item, isCurrent) }
    override suspend fun deletePersonal(owner: CourseOwner, id: String, isCurrent: () -> Boolean) = storage { dao.delete(owner, id, isCurrent) }
    private fun <T> Flow<T>.storageErrors(): Flow<T> = catch { if (it is SQLiteException) throw CourseCacheStorageException(it); throw it }
    private suspend fun <T> storage(block: suspend () -> T): T = try { block() } catch (error: SQLiteException) { throw CourseCacheStorageException(error) }
}
