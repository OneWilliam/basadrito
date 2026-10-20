package org.basadrito.app.data.moodle

import org.basadrito.app.data.moodle.dto.Assignment
import org.basadrito.app.data.moodle.dto.Course
import org.basadrito.app.data.moodle.dto.CourseSection
import org.basadrito.app.data.moodle.dto.GradeResponse
import org.basadrito.app.data.moodle.dto.MoodleSession
import org.basadrito.app.data.moodle.dto.SiteInfo
import org.basadrito.app.data.moodle.dto.SubmissionStatus
import org.basadrito.app.data.moodle.MoodleClient
import org.basadrito.app.data.http.apiCall
import kotlin.coroutines.cancellation.CancellationException


class MoodleDataSource(
    private val api: MoodleClient,
) {

    suspend fun login(username: String, password: String): Result<MoodleSession> = apiCall {
        val token = atStep(MoodleLoginStep.TOKEN) {
            api.getToken(username, password).getOrThrow().token?.takeIf { it.isNotBlank() }
                ?: error("Moodle rechazó las credenciales.")
        }
        val site = atStep(MoodleLoginStep.SITE_INFO) {
            api.getSiteInfo(token).getOrThrow().also {
                require(it.userId > 0) { "Moodle no devolvió una identidad válida." }
            }
        }

        MoodleSession(
            token = token,
            userId = site.userId,
            fullName = site.fullname.ifBlank { "${site.firstName} ${site.lastName}".trim() },
            username = site.username.ifBlank { username },
        )
    }

    private suspend fun <T> atStep(step: MoodleLoginStep, block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        throw MoodleLoginFailure(step, error)
    }

    suspend fun getSiteInfo(token: String): Result<SiteInfo> = api.getSiteInfo(token)

    suspend fun getCourses(token: String, userId: Int): Result<List<Course>> =
        api.getCourses(token, userId)

    suspend fun getGrades(token: String, userId: Int): Result<GradeResponse> =
        api.getGrades(token, userId)

    suspend fun getAssignments(token: String, courseIds: List<Int>): Result<List<Assignment>> =
        api.getAssignments(token, courseIds).map { response ->
            response.courses.flatMap { it.assignments }
        }

    suspend fun getSubmissionStatus(token: String, assignId: Int): Result<SubmissionStatus> =
        api.getSubmissionStatus(token, assignId)

    suspend fun getCourseContents(token: String, courseId: Int): Result<List<CourseSection>> =
        api.getCourseContents(token, courseId)

    suspend fun getCourseTeachers(token: String, courseIds: List<Int>): Result<Map<Int, String>> =
        apiCall {
            val teachers = mutableMapOf<Int, String>()
            for (courseId in courseIds) {
                val users = api.getEnrolledUsers(token, courseId).getOrNull() ?: continue
                val teacher = users.firstOrNull { user -> user.roles.any { it.isTeacher } }
                if (teacher != null) teachers[courseId] = teacher.fullname
            }
            teachers
        }
}
