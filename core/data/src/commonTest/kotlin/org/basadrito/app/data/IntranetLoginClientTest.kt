package org.basadrito.app.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.basadrito.app.data.http.createHttpClient
import org.basadrito.app.data.intranet.IntranetLoginClient
import org.basadrito.app.data.intranet.IntranetLoginFailure
import org.basadrito.app.data.intranet.IntranetLoginStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IntranetLoginClientTest {
    private fun engine(
        credentialsStatus: HttpStatusCode = HttpStatusCode.Found,
        authorizationHasCode: Boolean = true,
        profileStatus: HttpStatusCode = HttpStatusCode.OK,
        profileHtml: String = """<span class="m-card-profile__name">Estudiante</span>""",
    ) = MockEngine { request ->
        when {
            request.url.encodedPath == "/login" && request.method == HttpMethod.Get ->
                respond("""<input name="__RequestVerificationToken" value="fixture-verification">""")
            request.url.encodedPath == "/login" -> {
                val form = (request.body as FormDataContent).formData
                assertEquals("student", form["UserName"])
                assertEquals("fixture-password", form["Password"])
                assertEquals("fixture-verification", form["__RequestVerificationToken"])
                respond("", credentialsStatus,
                    headersOf(HttpHeaders.SetCookie to listOf("campus_auth=fixture; Path=/; Secure"),
                        HttpHeaders.Location to listOf("/home")))
            }
            request.url.host == "intranet.unjbg.edu.pe" && request.url.encodedPath in setOf("", "/") ->
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://campus.unjbg.edu.pe/authorize"))
            request.url.encodedPath == "/authorize" -> {
                assertTrue(request.headers[HttpHeaders.Cookie].orEmpty().contains("campus_auth=fixture"))
                respond(if (authorizationHasCode) """<input name="code" value="fixture-code"><input name="state" value="fixture-state">""" else "Acceso rechazado")
            }
            request.url.encodedPath == "/signin-oidc" -> {
                val form = (request.body as FormDataContent).formData
                assertEquals("fixture-code", form["code"])
                assertEquals("fixture-state", form["state"])
                respond("", HttpStatusCode.Found,
                    headersOf(HttpHeaders.SetCookie to listOf("intranet_auth=fixture; Path=/; Secure"),
                        HttpHeaders.Location to listOf("/perfil")))
            }
            request.url.encodedPath == "/perfil" -> {
                assertTrue(request.headers[HttpHeaders.Cookie].orEmpty().contains("intranet_auth=fixture"))
                respond(profileHtml, profileStatus)
            }
            else -> error("Solicitud inesperada en fixture")
        }
    }

    @Test
    fun expectedRedirectsPreserveCookiesAndLoginConfirmsProfile() = runTest {
        val client = createHttpClient(engine())
        try {
            val login = IntranetLoginClient(client)
            assertTrue(login.login("student", "fixture-password").isSuccess)
            assertTrue(login.isAuthenticated())
        } finally { client.close() }
    }

    @Test
    fun credentialHttpErrorReportsSafeStageAndStatus() = runTest {
        val client = createHttpClient(engine(credentialsStatus = HttpStatusCode.Forbidden))
        try {
            val failure = assertIs<IntranetLoginFailure>(IntranetLoginClient(client).login("student", "fixture-password").exceptionOrNull())
            assertEquals(IntranetLoginStep.CREDENTIALS, failure.step)
            assertEquals(403, failure.httpStatus)
            assertFalse(failure.message.orEmpty().contains("fixture-password"))
        } finally { client.close() }
    }

    @Test
    fun missingCodeIdentifiesAuthorizationStage() = runTest {
        val client = createHttpClient(engine(authorizationHasCode = false))
        try {
            val failure = assertIs<IntranetLoginFailure>(IntranetLoginClient(client).login("student", "fixture-password").exceptionOrNull())
            assertEquals(IntranetLoginStep.AUTHORIZATION, failure.step)
        } finally { client.close() }
    }

    @Test
    fun cookieWithoutAccessToProtectedProfileIsNotSuccessfulLogin() = runTest {
        val client = createHttpClient(engine(profileStatus = HttpStatusCode.Found))
        try {
            val failure = assertIs<IntranetLoginFailure>(IntranetLoginClient(client).login("student", "fixture-password").exceptionOrNull())
            assertEquals(IntranetLoginStep.PROFILE, failure.step)
            assertEquals(302, failure.httpStatus)
        } finally { client.close() }
    }

    @Test
    fun loginFormWithHttp200IsNotSuccessfulLogin() = runTest {
        val client = createHttpClient(engine(profileHtml = """<input name="UserName"><input name="Password">"""))
        try {
            val failure = assertIs<IntranetLoginFailure>(IntranetLoginClient(client).login("student", "fixture-password").exceptionOrNull())
            assertEquals(IntranetLoginStep.PROFILE, failure.step)
            assertEquals(200, failure.httpStatus)
            assertFalse(IntranetLoginClient(client).isAuthenticated())
        } finally { client.close() }
    }
}
