package org.basadrito.app.data.intranet

import org.basadrito.app.data.http.apiCall
import org.basadrito.app.data.intranet.dto.StudentProfile
import org.basadrito.app.data.intranet.parser.IntranetHtmlParser

class IntranetDataSource (
    private val auth: IntranetLoginClient,
    private val api: IntranetClient,
) {

    suspend fun login(username: String, password: String): Result<Unit> = auth.login(username, password)

    suspend fun isAuthenticated(): Boolean = auth.isAuthenticated()

    suspend fun getProfile(): Result<StudentProfile> = apiCall {
        IntranetHtmlParser.parseProfile(api.getText(PATH_PROFILE).getOrThrow())
    }

    private companion object {
        private const val PATH_PROFILE = "/perfil"
    }
}
