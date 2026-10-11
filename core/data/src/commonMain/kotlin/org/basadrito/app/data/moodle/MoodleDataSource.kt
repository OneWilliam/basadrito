package org.basadrito.app.data.moodle

import org.basadrito.app.data.moodle.dto.Course
import org.basadrito.app.data.moodle.dto.MoodleSession
import org.basadrito.app.data.moodle.dto.SiteInfo
import org.basadrito.app.data.http.apiCall
import org.basadrito.app.data.moodle.MoodleClient

class MoodleDataSource(
    private val api: MoodleClient,
)
{
    suspend fun login(username: String, password: String): Result<MoodleSession> = apiCall {
        val tokenResponse = api.getToken(username, password).getOrThrow()
        val token = tokenResponse.token?.takeIf { it.isNotBlank() } ?: error("Moodle rechazó las credenciales.")

        val site = api.getSiteInfo(token).getOrThrow()
        require(site.userId > 0) { "El usuario no es válido." }

        MoodleSession(
            token = token,
            userId = site.userId,
            fullName = site.fullname.ifBlank { "${site.firstName} ${site.lastName}".trim() },
            username = site.username.ifBlank { username }
        )
    }

    suspend fun getSiteInfo(token: String): Result<SiteInfo> = api.getSiteInfo(token)
    suspend fun getCourses(token: String, userId: Int): Result<List<Course>> = api.getCourses(token, userId)
}
