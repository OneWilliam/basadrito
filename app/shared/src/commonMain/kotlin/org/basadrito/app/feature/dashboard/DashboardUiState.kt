package org.basadrito.app.feature.dashboard

import org.basadrito.app.domain.courses.CourseSummary

data class DashboardUiState(
    val courses: List<CourseSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
