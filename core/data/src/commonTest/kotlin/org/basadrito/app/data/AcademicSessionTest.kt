package org.basadrito.app.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.basadrito.app.data.di.IntegrationClients
import org.basadrito.app.data.http.createHttpClient
import org.basadrito.app.data.moodle.dto.MoodleSession
import org.basadrito.app.data.session.AcademicSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AcademicSessionTest {
    @Test
    fun switchingAccountsDoesNotReuseTokenOrCookies() = runTest {
        val receivedCookies = mutableListOf<Pair<Int, String>>()
        var generation = 0
        val session = AcademicSession {
            val account = ++generation
            IntegrationClients(createHttpClient(MockEngine { request ->
                receivedCookies += account to request.headers[HttpHeaders.Cookie].orEmpty()
                respond("fixture", headers = headersOf(HttpHeaders.SetCookie, "session=account-$account; Path=/; Secure"))
            }))
        }
        try {
            val first = session.begin()
            assertTrue(session.activate(first, MoodleSession(token = "fixture-token-a")))
            first.clients.httpClient.get("https://intranet.unjbg.edu.pe/perfil")
            first.clients.httpClient.get("https://intranet.unjbg.edu.pe/perfil")
            assertTrue(receivedCookies.last().second.contains("session=account-1"))

            val second = session.begin()
            assertNotSame(first.clients, second.clients)
            assertNull(first.moodle)
            assertNull(second.moodle)
            first.clients.httpClient.coroutineContext[Job]!!.join()
            assertFalse(first.clients.httpClient.coroutineContext[Job]!!.isActive)
            second.clients.httpClient.get("https://intranet.unjbg.edu.pe/perfil")
            assertEquals(2 to "", receivedCookies.last())
            assertTrue(session.activate(second, MoodleSession(token = "fixture-token-b")))
            assertEquals("fixture-token-b", second.moodle?.token)
        } finally { session.clear() }
    }

    @Test
    fun logoutRejectsLateActivationAndRemovesToken() {
        val session = AcademicSession { IntegrationClients(createHttpClient(MockEngine { respond("fixture") })) }
        try {
            val candidate = session.begin()
            assertTrue(session.activate(candidate, MoodleSession(token = "fixture-token")))
            session.clear()
            assertNull(candidate.moodle)
            assertFalse(session.isCurrent(candidate))
            assertFalse(candidate.clients.httpClient.coroutineContext[Job]!!.isActive)
            assertFalse(session.activate(candidate, MoodleSession(token = "late-token")))
            assertNull(candidate.moodle)
            session.clear()
        } finally { session.clear() }
    }

    @Test
    fun oldLoginCannotActivateOrDiscardNewAccount() {
        val session = AcademicSession { IntegrationClients(createHttpClient(MockEngine { respond("fixture") })) }
        try {
            val old = session.begin()
            val current = session.begin()
            assertFalse(session.activate(old, MoodleSession(token = "stale-token")))
            session.discard(old)
            assertTrue(session.isCurrent(current))
            assertTrue(current.clients.httpClient.coroutineContext[Job]!!.isActive)
            assertTrue(session.activate(current, MoodleSession(token = "current-token")))
        } finally { session.clear() }
    }
}
