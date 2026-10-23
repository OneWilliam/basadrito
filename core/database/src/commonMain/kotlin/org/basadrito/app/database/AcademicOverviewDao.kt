package org.basadrito.app.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.basadrito.app.data.cache.CourseOwner
import org.basadrito.app.domain.academic.*
import kotlin.coroutines.cancellation.CancellationException

@Entity(tableName = "academic_snapshots", indices = [Index(value = ["provider", "ownerId"], unique = true)])
data class AcademicSnapshotEntity(@PrimaryKey val accountKey: String, val provider: String, val ownerId: Int,
    val approvedCredits: Double?, val totalCredits: Double?, val creditsAtMillis: Long?,
    val scheduleAtMillis: Long?, val attendanceAtMillis: Long?, val teachersAtMillis: Long?, val tasksAtMillis: Long?)

@Entity(tableName = "academic_classes", primaryKeys = ["accountKey", "position"],
    foreignKeys = [ForeignKey(entity = AcademicSnapshotEntity::class, parentColumns = ["accountKey"],
        childColumns = ["accountKey"], onDelete = ForeignKey.CASCADE)])
data class AcademicClassEntity(val accountKey: String, val position: Int, val course: String, val detail: String,
    val day: String, val startTime: String, val endTime: String, val startIso: String, val endIso: String)

@Entity(tableName = "academic_attendance", primaryKeys = ["accountKey", "position"],
    foreignKeys = [ForeignKey(entity = AcademicSnapshotEntity::class, parentColumns = ["accountKey"],
        childColumns = ["accountKey"], onDelete = ForeignKey.CASCADE)])
data class AcademicAttendanceEntity(val accountKey: String, val position: Int, val course: String,
    val attended: Int, val dictated: Int)

@Entity(tableName = "academic_teachers", primaryKeys = ["accountKey", "courseId"],
    foreignKeys = [ForeignKey(entity = AcademicSnapshotEntity::class, parentColumns = ["accountKey"],
        childColumns = ["accountKey"], onDelete = ForeignKey.CASCADE)])
data class AcademicTeacherEntity(val accountKey: String, val courseId: Int, val name: String)

@Entity(tableName = "academic_tasks", primaryKeys = ["accountKey", "taskId"],
    foreignKeys = [ForeignKey(entity = AcademicSnapshotEntity::class, parentColumns = ["accountKey"],
        childColumns = ["accountKey"], onDelete = ForeignKey.CASCADE)])
data class AcademicTaskEntity(val accountKey: String, val taskId: Int, val courseId: Int, val name: String,
    val dueAtEpochSeconds: Long?, val opensAtEpochSeconds: Long?, val submission: String, val graded: Boolean?)

data class AcademicGraph(
    @Embedded val snapshot: AcademicSnapshotEntity,
    @Relation(parentColumn = "accountKey", entityColumn = "accountKey") val classes: List<AcademicClassEntity>,
    @Relation(parentColumn = "accountKey", entityColumn = "accountKey") val attendance: List<AcademicAttendanceEntity>,
    @Relation(parentColumn = "accountKey", entityColumn = "accountKey") val teachers: List<AcademicTeacherEntity>,
    @Relation(parentColumn = "accountKey", entityColumn = "accountKey") val tasks: List<AcademicTaskEntity>,
) {
    fun toSnapshot(): AcademicSnapshot {
        val dates = listOf(AcademicSection.CREDITS to snapshot.creditsAtMillis,
            AcademicSection.SCHEDULE to snapshot.scheduleAtMillis, AcademicSection.ATTENDANCE to snapshot.attendanceAtMillis,
            AcademicSection.TEACHERS to snapshot.teachersAtMillis, AcademicSection.TASKS to snapshot.tasksAtMillis)
            .mapNotNull { (section, time) -> time?.let { section to it } }.toMap()
        return AcademicSnapshot(
            credits = snapshot.totalCredits?.let { CreditProgress(requireNotNull(snapshot.approvedCredits), it) },
            classes = if (snapshot.scheduleAtMillis == null) null else classes.sortedBy { it.position }.map {
                AcademicClass(it.course, it.detail, it.day, it.startTime, it.endTime, it.startIso, it.endIso) },
            attendance = if (snapshot.attendanceAtMillis == null) null else attendance.sortedBy { it.position }.map {
                CourseAttendance(it.course, it.attended, it.dictated) },
            teachers = if (snapshot.teachersAtMillis == null) null else teachers.associate { it.courseId to it.name },
            tasks = if (snapshot.tasksAtMillis == null) null else tasks.sortedWith(compareBy<AcademicTaskEntity> {
                it.dueAtEpochSeconds ?: Long.MAX_VALUE }.thenBy { it.taskId }).map {
                AcademicTask(it.taskId, it.courseId, it.name, it.dueAtEpochSeconds, it.opensAtEpochSeconds,
                    SubmissionState.valueOf(it.submission), it.graded) },
            updatedAtMillis = dates,
        )
    }
}

internal fun CourseOwner.academicKey() = "${provider.length}:$provider:$userId"

@Dao
abstract class AcademicOverviewDao {
    @Transaction @Query("SELECT * FROM academic_snapshots WHERE provider = :provider AND ownerId = :ownerId")
    abstract fun observe(provider: String, ownerId: Int): Flow<AcademicGraph?>
    @Transaction @Query("SELECT * FROM academic_snapshots WHERE provider = :provider AND ownerId = :ownerId")
    abstract suspend fun read(provider: String, ownerId: Int): AcademicGraph?
    @Upsert abstract suspend fun put(snapshot: AcademicSnapshotEntity)
    @Insert abstract suspend fun putClasses(rows: List<AcademicClassEntity>)
    @Insert abstract suspend fun putAttendance(rows: List<AcademicAttendanceEntity>)
    @Insert abstract suspend fun putTeachers(rows: List<AcademicTeacherEntity>)
    @Insert abstract suspend fun putTasks(rows: List<AcademicTaskEntity>)
    @Query("DELETE FROM academic_classes WHERE accountKey = :key") abstract suspend fun clearClasses(key: String)
    @Query("DELETE FROM academic_attendance WHERE accountKey = :key") abstract suspend fun clearAttendance(key: String)
    @Query("DELETE FROM academic_teachers WHERE accountKey = :key") abstract suspend fun clearTeachers(key: String)
    @Query("DELETE FROM academic_tasks WHERE accountKey = :key") abstract suspend fun clearTasks(key: String)

    @Transaction
    open suspend fun replace(owner: CourseOwner, data: AcademicSnapshot, isCurrent: () -> Boolean): Boolean {
        if (!isCurrent()) return false
        val key = owner.academicKey()
        put(AcademicSnapshotEntity(key, owner.provider, owner.userId, data.credits?.approved, data.credits?.total,
            data.updatedAtMillis[AcademicSection.CREDITS], data.updatedAtMillis[AcademicSection.SCHEDULE],
            data.updatedAtMillis[AcademicSection.ATTENDANCE], data.updatedAtMillis[AcademicSection.TEACHERS],
            data.updatedAtMillis[AcademicSection.TASKS]))
        clearClasses(key); clearAttendance(key); clearTeachers(key); clearTasks(key)
        putClasses(data.classes.orEmpty().mapIndexed { index, c -> AcademicClassEntity(key, index, c.course, c.detail,
            c.day, c.startTime, c.endTime, c.startIso, c.endIso) })
        putAttendance(data.attendance.orEmpty().mapIndexed { index, a -> AcademicAttendanceEntity(key, index, a.course, a.attended, a.dictated) })
        putTeachers(data.teachers.orEmpty().map { (id, name) -> AcademicTeacherEntity(key, id, name) })
        putTasks(data.tasks.orEmpty().map { AcademicTaskEntity(key, it.id, it.courseId, it.name,
            it.dueAtEpochSeconds, it.opensAtEpochSeconds, it.submission.name, it.graded) })
        if (!isCurrent()) throw CancellationException("La cuenta cambió durante la actualización académica.")
        return true
    }
}
