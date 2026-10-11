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
import kotlinx.serialization.json.decodeFromJsonElement

class MoodleClient(private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL
) {

    companion object {
        const val DEFAULT_BASE_URL = "https://aula.unjbg.edu.pe"
    }

    private suspend inline fun <reified T> request(token: String, function: String, args: Parameters = Parameters.Empty): T {
        val response = client.submitForm(endpointUrl(baseUrl, "webservice/rest/server.php"), Parameters.build {
            append("wstoken", token)
            append("wsfunction", function)
            append("moodlewsrestformat", "json")
            appendAll(args)
        })

        val text = response.bodyAsText()

        val parsed = AppJson.parseToJsonElement(text)

        if (parsed is JsonObject && ("exception" in parsed || "errorcode" in parsed || "error" in parsed)) {
            error("Moodle rechazo la consulta.")
        }
        return AppJson.decodeFromJsonElement<T>(parsed)
    }

    suspend fun getToken(username: String, password: String): Result<MoodleTokenResponse> = apiCall {
        val response = client.submitForm(endpointUrl(baseUrl, "login/token.php"),
            Parameters.build {
                append("username", username)
                append("password", password)
                append("service", MoodleFunctions.SERVICE)
            })
        AppJson.decodeFromString<MoodleTokenResponse>(response.bodyAsText())

    }

    suspend fun getCourses(token: String, userId: Int): Result<List<Course>> = apiCall {
        request(token, MoodleFunctions.USER_COURSES, Parameters.build {
            append("userid", userId.toString())
        })
    }

    suspend fun getSiteInfo(token: String): Result<SiteInfo> = apiCall {
        request(token, MoodleFunctions.SITE_INFO)
    }
}
