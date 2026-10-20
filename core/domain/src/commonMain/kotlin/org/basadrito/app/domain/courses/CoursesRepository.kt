package org.basadrito.app.domain.courses

data class CourseSummary(val id: Int, val name: String, val shortName: String)

interface CoursesRepository {
    suspend fun getCourses(): Result<List<CourseSummary>>
}
