package org.basadrito.app.data.intranet

import org.basadrito.app.data.http.apiCall
import org.basadrito.app.data.http.endpointUrl
import org.basadrito.app.data.http.getTextFollowingRedirects
import org.basadrito.app.data.intranet.parser.IntranetSessionParser
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import org.basadrito.app.data.http.resolveLocation
import kotlin.coroutines.cancellation.CancellationException


class IntranetLoginClient(
    private val client: HttpClient,
    private val campusBaseUrl: String = DEFAULT_CAMPUS_URL,
    private val intranetBaseUrl: String = DEFAULT_INTRANET_URL,
) {

    private val loginUrl = endpointUrl(campusBaseUrl, "login")
    private val signInOidcUrl = endpointUrl(intranetBaseUrl, "signin-oidc")

    suspend fun login(username: String, password: String): Result<Unit> = apiCall {
        val verificationToken = atStep(IntranetLoginStep.LOGIN_PAGE) {
            val loginHtml = client.getTextFollowingRedirects(loginUrl)
            org.basadrito.app.data.intranet.parser.hiddenInputValue(loginHtml, "__RequestVerificationToken")
                ?.takeIf { it.isNotBlank() } ?: throw IntranetLoginFailure(IntranetLoginStep.LOGIN_PAGE)
        }

        atStep(IntranetLoginStep.CREDENTIALS) {
            val response = client.submitForm(
                url = loginUrl,
                formParameters = Parameters.build {
                    append("UserName", username)
                    append("Password", password)
                    append("System", "CAMPUS")
                    append("SystemLogin", "0")
                    append("__RequestVerificationToken", verificationToken)
                },
            ) { expectSuccess = false }
            if (response.status.value !in 200..399) {
                throw IntranetLoginFailure(IntranetLoginStep.CREDENTIALS, response.status.value)
            }
        }

        val (code, state, sessionState) = atStep(IntranetLoginStep.AUTHORIZATION) {
            val authorizeHtml = client.getTextFollowingRedirects(intranetBaseUrl)
            val code = authorizeHtml.findHidden("code")
                ?.takeIf { it.isNotBlank() } ?: throw IntranetLoginFailure(IntranetLoginStep.AUTHORIZATION)
            Triple(code, authorizeHtml.findHidden("state") ?: "", authorizeHtml.findHidden("session_state") ?: "")
        }

        atStep(IntranetLoginStep.CALLBACK) {
            val response = client.submitForm(
                url = signInOidcUrl,
                formParameters = Parameters.build {
                    append("code", code)
                    append("scope", "openid profile roles")
                    append("state", state)
                    append("session_state", sessionState)
                },
            ) { expectSuccess = false }
            if (response.status.value !in 200..399) {
                throw IntranetLoginFailure(IntranetLoginStep.CALLBACK, response.status.value)
            }
        }
        verifyProfile()
    }

    private suspend fun verifyProfile() = atStep(IntranetLoginStep.PROFILE) {
        val response = client.get(endpointUrl(intranetBaseUrl, "perfil")) { expectSuccess = false }
        if (response.status.value in 300..399) {
            val location = response.headers[HttpHeaders.Location]
            if (location != null) {
                val target = Url(resolveLocation(response.call.request.url.toString(), location))
                if (target.host == Url(intranetBaseUrl).host &&
                    target.encodedPath.trimEnd('/') == EVALUATION_PATH) {
                    throw IntranetLoginFailure(IntranetLoginStep.EVALUATION_REQUIRED, response.status.value)
                }
                if (target.host == Url(campusBaseUrl).host || target.encodedPath.trimEnd('/') == "/login") {
                    throw IntranetLoginFailure(IntranetLoginStep.PROFILE, response.status.value, IntranetSessionExpired())
                }
            }
        }
        if (response.status.value in setOf(401, 403)) {
            throw IntranetLoginFailure(IntranetLoginStep.PROFILE, response.status.value, IntranetSessionExpired())
        }
        if (response.status.value != 200) throw IntranetLoginFailure(IntranetLoginStep.PROFILE, response.status.value)
        val html = response.bodyAsText()
        if (IntranetSessionParser.isLoginForm(html)) {
            throw IntranetLoginFailure(IntranetLoginStep.PROFILE, response.status.value, IntranetSessionExpired())
        }
        if (!IntranetSessionParser.isAuthenticatedProfile(html)) {
            throw IntranetLoginFailure(IntranetLoginStep.PROFILE, response.status.value)
        }
    }

    suspend fun checkAccess(): Result<Unit> = apiCall { verifyProfile() }

    suspend fun isAuthenticated(): Boolean = apiCall {
        verifyProfile()
        true
    }.getOrDefault(false)

    private fun String.findHidden(name: String): String? =
        org.basadrito.app.data.intranet.parser.hiddenInputValue(this, name)

    private suspend fun <T> atStep(step: IntranetLoginStep, block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: IntranetLoginFailure) {
        throw failure
    } catch (http: ResponseException) {
        throw IntranetLoginFailure(step, http.response.status.value, http)
    } catch (error: Exception) {
        throw IntranetLoginFailure(step, cause = error)
    }

    companion object {
        const val DEFAULT_CAMPUS_URL = "https://campus.unjbg.edu.pe"
        const val DEFAULT_INTRANET_URL = "https://intranet.unjbg.edu.pe"
        const val EVALUATION_PATH = "/alumno/evaluacion-docente"
    }
}
