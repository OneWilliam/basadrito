package org.basadrito.app.domain.academic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class CreditProgress(val approved: Double, val total: Double) {
    init { require(total > 0 && total.isFinite() && approved.isFinite() && approved in 0.0..total) }
    val fraction: Double get() = approved / total
}

data class AcademicClass(val course: String, val detail: String, val day: String,
    val startTime: String, val endTime: String, val startIso: String, val endIso: String)

data class CourseAttendance(val course: String, val attended: Int, val dictated: Int) {
    val percentage: Double? get() = if (dictated > 0 && attended in 0..dictated) attended * 100.0 / dictated else null
}

enum class SubmissionState { UNKNOWN, NEW, DRAFT, SUBMITTED, REOPENED }

data class AcademicTask(val id: Int, val courseId: Int, val name: String,
    val dueAtEpochSeconds: Long?, val opensAtEpochSeconds: Long?,
    val submission: SubmissionState, val graded: Boolean?) {
    val pending: Boolean? get() = when {
        submission == SubmissionState.UNKNOWN -> null
        graded == true || submission == SubmissionState.SUBMITTED -> false
        else -> true
    }
}

enum class AcademicSection { CREDITS, SCHEDULE, ATTENDANCE, TEACHERS, TASKS }

/** Nullable sections are unavailable; an empty successful list is a different state. */
data class AcademicSnapshot(
    val credits: CreditProgress? = null,
    val classes: List<AcademicClass>? = null,
    val attendance: List<CourseAttendance>? = null,
    val teachers: Map<Int, String>? = null,
    val tasks: List<AcademicTask>? = null,
    val updatedAtMillis: Map<AcademicSection, Long> = emptyMap(),
)

data class AcademicFeed(val snapshot: AcademicSnapshot = AcademicSnapshot(), val isRefreshing: Boolean = false,
    val failedSections: Set<AcademicSection> = emptySet(), val storageFailed: Boolean = false)

interface AcademicRepository {
    fun observe(): Flow<AcademicFeed>
    suspend fun refresh(initial: Boolean = false)
}

/** Optional for hosts that intentionally expose only the basic course integration. */
object NoAcademicRepository : AcademicRepository {
    override fun observe(): Flow<AcademicFeed> = emptyFlow()
    override suspend fun refresh(initial: Boolean) = Unit
}
