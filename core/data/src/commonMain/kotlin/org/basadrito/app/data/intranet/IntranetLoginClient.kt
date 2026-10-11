package org.basadrito.app.data.intranet

import org.basadrito.app.data.intranet.parser.IntranetSessionParser
import org.basadrito.app.data.http.apiCall
import org.basadrito.app.data.http.endpointUrl
import org.basadrito.app.data.http.AppJson
import org.basadrito.app.data.http.getTextFollowingRedirects
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.client.plugins.expectSuccess
import kotlin.coroutines.cancellation.CancellationException
import io.ktor.client.plugins.ResponseException
import org.basadrito.app.data.intranet.parser.IntranetHtmlParser

class IntranetLoginClient(
    private val client: HttpClient,
    private val campusBaseUrl: String = DEFAULT_CAMPUS_URL,
    private val intranetBaseUrl: String = DEFAULT_INTRANET_URL
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
      if (response.status.value != 200) throw IntranetLoginFailure(IntranetLoginStep.PROFILE, response.status.value)
      if (!IntranetSessionParser.isAuthenticatedProfile(response.bodyAsText())) {
          throw IntranetLoginFailure(IntranetLoginStep.PROFILE, response.status.value)
      }
  }


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
          throw IntranetLoginFailure(step, http.response.status.value)
      } catch (_: Exception) {
          throw IntranetLoginFailure(step)
      }

  companion object {
      const val DEFAULT_CAMPUS_URL = "https://campus.unjbg.edu.pe"
      const val DEFAULT_INTRANET_URL = "https://intranet.unjbg.edu.pe"
  }
}
