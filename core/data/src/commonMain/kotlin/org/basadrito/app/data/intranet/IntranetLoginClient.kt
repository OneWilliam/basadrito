package org.basadrito.app.data.intranet

import org.basadrito.app.data.http.apiCall
import org.basadrito.app.data.http.endpointUrl
import org.basadrito.app.data.http.AppJson
import org.basadrito.app.data.http.getTextFollowingRedirects
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters


class IntranetLoginClient(
    private val client: HttpClient,
    private val campusBaseUrl: String = DEFAULT_CAMPUS_URL,
    private val intranetBaseUrl: String = DEFAULT_INTRANET_URL
) {
  private val loginUrl = endpointUrl(campusBaseUrl, "login")

  private val signInOidcUrl = endpointUrl(intranetBaseUrl, "signin-oidc")

  suspend fun login(username: String, password: String): Result<Unit> = apiCall {
      val loginHtml = client.getTextFollowingRedirects(loginUrl)
      val verificationUrl = org.basadrito.app.data.intranet.parser.hiddenInputValue(loginHtml, "__RequestVerificationToken")
      ?: error ("No se encontró __RequestVerificationToken en $loginUrl")

      client.submitForm(
          url = loginUrl,
          formParameters = Parameters.build {
              append("username", username)
              append("password", password)
              append("sytem", "CAMPUS")
              append("SytemLogin", "0")
              append("__RequestVerificationToken", verificationUrl)
          }
      )

      val authorizeHtml = client.getTextFollowingRedirects(intranetBaseUrl)

      val code = authorizeHtml.findHidden("code")
      ?: error("No se encontró code en la respuesta de authorize. Credenciales Incorrectas?")
      val state = authorizeHtml.findHidden("state") ?: ""
      val sessionState = authorizeHtml.findHidden("session_state") ?: ""

      val response = client.submitForm(
          url = signInOidcUrl,
          formParameters = Parameters.build {
              append("code", code)
              append("scope", "openid profile roles")
              append("state", state)
              append("session_state", sessionState)
          }

      )

      require(response.status.value in 200..399) {"Intranet rechazo el acceso."}
      require(isAuthenticated()) {
          "No se pudo comprobar la sesión de Intranet."
      }


  }

  suspend fun isAuthenticated(): Boolean = apiCall {
      val response = client.get(endpointUrl(intranetBaseUrl, "perfil"))
      if(response.status.value != 200) return@apiCall false
      val profile  = org.basadrito.app.data.intranet.parser.IntranetHtmlParser.parseProfile(response.bodyAsText())
      profile.code.isNotBlank() && profile.fullName.isNotBlank()
  }.getOrDefault(false)

  private fun String.findHidden(name: String): String? =
      org.basadrito.app.data.intranet.parser.hiddenInputValue(this, name)

  companion object {
      const val DEFAULT_CAMPUS_URL = "https://campus.unjbg.edu.pe"
      const val DEFAULT_INTRANET_URL = "https://intranet.unjbg.edu.pe"
  }
}
