package org.basadrito.app.database

import androidx.room.*
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException
import org.basadrito.app.data.cache.CachedCourses
import org.basadrito.app.data.cache.CourseOwner
import org.basadrito.app.domain.courses.CourseSummary

@Entity(tableName = "course_snapshots", primaryKeys = ["provider", "ownerId"])
data class CourseSnapshotEntity(val provider: String, val ownerId: Int, val updatedAtMillis: Long)

@Entity(tableName = "courses", primaryKeys = ["provider", "ownerId", "courseId"])
data class CourseEntity(val provider: String, val ownerId: Int, val courseId: Int,
    val name: String, val shortName: String, val position: Int,
    val startsAtEpochSeconds: Long? = null, val endsAtEpochSeconds: Long? = null)

/** One SQL statement keeps metadata and course rows from the same committed snapshot. */
data class ObservedCourseRow(val updatedAtMillis: Long, val courseId: Int?, val name: String?, val shortName: String?,
    val startsAtEpochSeconds: Long?, val endsAtEpochSeconds: Long?)

@Dao
abstract class CourseDao {
    @Query("""
        SELECT s.updatedAtMillis, c.courseId, c.name, c.shortName, c.startsAtEpochSeconds, c.endsAtEpochSeconds
        FROM course_snapshots AS s LEFT JOIN courses AS c
        ON s.provider = c.provider AND s.ownerId = c.ownerId
        WHERE s.provider = :provider AND s.ownerId = :ownerId ORDER BY c.position
    """)
    abstract fun observe(provider: String, ownerId: Int): Flow<List<ObservedCourseRow>>

    @Query("SELECT * FROM course_snapshots WHERE provider = :provider AND ownerId = :ownerId")
    abstract suspend fun snapshot(provider: String, ownerId: Int): CourseSnapshotEntity?
    @Query("SELECT * FROM courses WHERE provider = :provider AND ownerId = :ownerId ORDER BY position")
    abstract suspend fun courses(provider: String, ownerId: Int): List<CourseEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putSnapshot(snapshot: CourseSnapshotEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putCourses(courses: List<CourseEntity>)
    @Query("DELETE FROM courses WHERE provider = :provider AND ownerId = :ownerId")
    abstract suspend fun deleteCourses(provider: String, ownerId: Int)
    @Query("DELETE FROM course_snapshots WHERE provider = :provider AND ownerId = :ownerId")
    abstract suspend fun deleteSnapshot(provider: String, ownerId: Int)

    @Transaction
    open suspend fun read(owner: CourseOwner): CachedCourses? {
        val snapshot = snapshot(owner.provider, owner.userId) ?: return null
        return CachedCourses(courses(owner.provider, owner.userId).map { CourseSummary(it.courseId, it.name, it.shortName,
            it.startsAtEpochSeconds, it.endsAtEpochSeconds) }, snapshot.updatedAtMillis)
    }

    @Transaction
    open suspend fun replace(owner: CourseOwner, snapshot: CachedCourses, isCurrent: () -> Boolean): Boolean {
        if (!isCurrent()) return false
        deleteCourses(owner.provider, owner.userId)
        putSnapshot(CourseSnapshotEntity(owner.provider, owner.userId, snapshot.updatedAtMillis))
        putCourses(snapshot.courses.mapIndexed { index, course ->
            CourseEntity(owner.provider, owner.userId, course.id, course.name, course.shortName, index,
                course.startsAtEpochSeconds, course.endsAtEpochSeconds)
        })
        // A logout/account switch during SQL execution rolls the whole replacement back.
        if (!isCurrent()) throw CancellationException("La cuenta cambió durante la actualización local.")
        return true
    }

    @Transaction
    open suspend fun delete(owner: CourseOwner) {
        deleteCourses(owner.provider, owner.userId)
        deleteSnapshot(owner.provider, owner.userId)
    }
}

@Database(entities = [CourseEntity::class, CourseSnapshotEntity::class, AcademicSnapshotEntity::class,
    AcademicClassEntity::class, AcademicAttendanceEntity::class, AcademicTeacherEntity::class, AcademicTaskEntity::class,
    CalendarMonthEntity::class, CalendarClassEntity::class, PersonalItemEntity::class],
    version = 2, autoMigrations = [AutoMigration(from = 1, to = 2)], exportSchema = true)
@ConstructedBy(AcademicDatabaseConstructor::class)
abstract class AcademicDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun academicOverviewDao(): AcademicOverviewDao
    abstract fun agendaDao(): AgendaDao
}

@Suppress("KotlinNoActualForExpect")
expect object AcademicDatabaseConstructor : RoomDatabaseConstructor<AcademicDatabase> {
    override fun initialize(): AcademicDatabase
}

internal fun buildAcademicDatabase(builder: RoomDatabase.Builder<AcademicDatabase>): AcademicDatabase =
    builder.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
