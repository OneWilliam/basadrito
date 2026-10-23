package org.basadrito.app.feature.dashboard

import org.basadrito.app.domain.courses.CourseSummary
import org.basadrito.app.domain.courses.CourseSource
import org.basadrito.app.domain.courses.CourseCacheWarning

data class DashboardUiState(
    val courses: List<CourseSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val source: CourseSource? = null,
    val updatedAtMillis: Long? = null,
    val refreshFailed: Boolean = false,
    val cacheWarning: CourseCacheWarning? = null,
)
