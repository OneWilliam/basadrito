package org.basadrito.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.basadrito.app.data.di.IntegrationClients
import org.basadrito.app.data.http.createHttpClient
import org.basadrito.app.data.repository.AuthRepositoryImpl
import org.basadrito.app.data.session.AcademicSession
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class AuthRepositoryTest {
    private enum class Failure { NONE, MOODLE_REJECTED, INTRANET_REJECTED, TRANSPORT, CANCELLED }

    private class Fixture(private val failure: Failure = Failure.NONE) {
        val clients = mutableListOf<HttpClient>()
        val requests = mutableListOf<String>()
        val session = AcademicSession {
            val client = createHttpClient(MockEngine { request ->
                val path = request.url.encodedPath.ifEmpty { "/" }
                requests += path
                when {
                    path.endsWith("token.php") -> {
                        if (failure == Failure.TRANSPORT) throw kotlinx.io.IOException("fixture transport")
                        respond(if (failure == Failure.MOODLE_REJECTED) """{"error":"denied"}"""
                            else """{"token":"fixture-token"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                    path.endsWith("server.php") -> respond(
                        """{"userid":12,"username":"student","fullname":"Estudiante ficticio"}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    path == "/login" && request.method == HttpMethod.Get -> {
                        if (failure == Failure.CANCELLED) throw CancellationException("fixture cancellation")
                        respond("""<input name="__RequestVerificationToken" value="fixture-verification">""")
                    }
                    path == "/login" -> respond("", if (failure == Failure.INTRANET_REJECTED)
                        HttpStatusCode.Forbidden else HttpStatusCode.OK)
                    path in setOf("", "/") -> respond("""<input name="code" value="fixture-code"><input name="state" value="fixture-state">""")
                    path == "/signin-oidc" -> respond("OK")
                    path == "/perfil" -> respond("""<span class="m-card-profile__name">Estudiante ficticio</span>""")
                    else -> error("Solicitud inesperada en fixture")
                }
            })
            clients += client
            IntegrationClients(client)
        }
        val auth = AuthRepositoryImpl(session)
    }

    @Test
    fun successRequiresBothProvidersAndProtectedProfile() = runTest {
        val fixture = Fixture()
        try {
            val access = fixture.auth.signIn("student", "fixture-moodle", "fixture-intranet").getOrThrow()
            assertTrue(access.intranetAvailable)
            assertEquals("Estudiante ficticio", access.student.fullName)
            assertEquals(listOf("/login/token.php", "/webservice/rest/server.php", "/login", "/login", "/", "/signin-oidc", "/perfil"), fixture.requests)
            assertTrue(fixture.clients.single().coroutineContext[Job]!!.isActive)
            fixture.auth.signOut()
            fixture.clients.single().coroutineContext[Job]!!.join()
            assertFalse(fixture.clients.single().coroutineContext[Job]!!.isActive)
        } finally { fixture.auth.signOut() }
    }

    @Test
    fun rejectedProviderOrTransportFailureClosesProvisionalClient() = runTest {
        for (failure in listOf(Failure.MOODLE_REJECTED, Failure.INTRANET_REJECTED, Failure.TRANSPORT)) {
            val fixture = Fixture(failure)
            try {
                val result = fixture.auth.signIn("student", "fixture-moodle", "fixture-intranet")
                assertTrue(result.isFailure)
                fixture.clients.single().coroutineContext[Job]!!.join()
                assertFalse(fixture.clients.single().coroutineContext[Job]!!.isActive)
                assertFalse("/perfil" in fixture.requests)
                val message = result.exceptionOrNull()?.message.orEmpty()
                assertFalse(message.contains("fixture-moodle"))
                assertFalse(message.contains("fixture-intranet"))
                assertFalse(message.contains("fixture-token"))
                if (failure != Failure.INTRANET_REJECTED) assertFalse("/login" in fixture.requests)
            } finally { fixture.auth.signOut() }
        }
    }

    @Test
    fun cancellationPropagatesAndClosesProvisionalClient() = runTest {
        val fixture = Fixture(Failure.CANCELLED)
        try {
            try {
                fixture.auth.signIn("student", "fixture-moodle", "fixture-intranet")
                fail("Se debe propagar CancellationException")
            } catch (_: CancellationException) {
                fixture.clients.single().coroutineContext[Job]!!.join()
                assertFalse(fixture.clients.single().coroutineContext[Job]!!.isActive)
                assertFalse("/signin-oidc" in fixture.requests)
            }
        } finally { fixture.auth.signOut() }
    }
}
