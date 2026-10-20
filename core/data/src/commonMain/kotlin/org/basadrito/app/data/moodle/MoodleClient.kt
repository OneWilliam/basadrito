package org.basadrito.app.data.moodle

import org.basadrito.app.data.http.apiCall
import org.basadrito.app.data.http.endpointUrl
import org.basadrito.app.data.http.AppJson
import org.basadrito.app.data.http.getTextFollowingRedirects
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.json.JsonObject
import org.basadrito.app.data.moodle.dto.*

class MoodleClient(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    suspend fun getToken(username: String, password: String): Result<MoodleTokenResponse> = apiCall {
        val response = client.submitForm(endpointUrl(baseUrl, "login/token.php"), Parameters.build {
            append("username", username); append("password", password); append("service", MoodleFunctions.SERVICE)
        })
        AppJson.decodeFromString<MoodleTokenResponse>(response.bodyAsText())
    }
    suspend fun getSiteInfo(token: String): Result<SiteInfo> = apiCall { request(token, MoodleFunctions.SITE_INFO) }
    suspend fun getCourses(token: String, userId: Int): Result<List<Course>> = apiCall {
        request(token, MoodleFunctions.USER_COURSES, Parameters.build { append("userid", userId.toString()) })
    }
    suspend fun getGrades(token: String, userId: Int): Result<GradeResponse> = apiCall {
        request(token, MoodleFunctions.COURSE_GRADES, Parameters.build { append("userid", userId.toString()) })
    }
    suspend fun getAssignments(token: String, courseIds: List<Int>): Result<AssignmentResponse> = apiCall {
        request(token, MoodleFunctions.ASSIGNMENTS, Parameters.build { courseIds.forEach { append("courseids[]", it.toString()) } })
    }
    suspend fun getSubmissionStatus(token: String, assignId: Int): Result<SubmissionStatus> = apiCall {
        request(token, MoodleFunctions.SUBMISSION_STATUS, Parameters.build { append("assignid", assignId.toString()) })
    }
    suspend fun getCourseContents(token: String, courseId: Int): Result<List<CourseSection>> = apiCall {
        request(token, MoodleFunctions.COURSE_CONTENTS, Parameters.build { append("courseid", courseId.toString()) })
    }
    suspend fun getEnrolledUsers(token: String, courseId: Int): Result<List<EnrolledUser>> = apiCall {
        request(token, MoodleFunctions.ENROLLED_USERS, Parameters.build { append("courseid", courseId.toString()) })
    }
    private suspend inline fun <reified T> request(token: String, function: String, args: Parameters = Parameters.Empty): T {
        val response = client.submitForm(endpointUrl(baseUrl, "webservice/rest/server.php"), Parameters.build {
            append("wstoken", token); append("wsfunction", function); append("moodlewsrestformat", "json"); appendAll(args)
        })
        val text = response.bodyAsText()
        val parsed = AppJson.parseToJsonElement(text)
        if (parsed is JsonObject && ("exception" in parsed || "errorcode" in parsed || "error" in parsed)) {
            error("Moodle rechazó la consulta.")
        }
        return AppJson.decodeFromString<T>(text)
    }
    companion object { const val DEFAULT_BASE_URL = "https://aula.unjbg.edu.pe" }
}
