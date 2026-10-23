package org.basadrito.app.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.cookies
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.basadrito.app.data.di.IntegrationClients
import org.basadrito.app.data.http.AppJson
import org.basadrito.app.data.http.createHttpClient
import org.basadrito.app.data.repository.AuthRepositoryImpl
import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.data.session.SavedSession
import org.basadrito.app.data.session.SessionStore
import org.basadrito.app.domain.auth.IntranetRequirement
import org.basadrito.app.domain.auth.SessionRecoveryException
import org.basadrito.app.domain.auth.SessionRecoveryFailure
import org.basadrito.app.domain.auth.AccessMode
import org.basadrito.app.data.cache.*
import org.basadrito.app.domain.courses.CourseSummary
import org.basadrito.app.data.diagnostics.*
import org.basadrito.app.data.repository.AuthenticationDiagnostics
import org.basadrito.app.data.repository.ConsoleAuthenticationDiagnostics
import io.ktor.client.HttpClient
import kotlinx.coroutines.Job
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionRecoveryTest {
    private class Store : SessionStore {
        var value: SavedSession? = null
        var loadFailure = false
        var saveFailure = false
        var saveGate: CompletableDeferred<Unit>? = null
        override suspend fun load(): SavedSession? {
            if (loadFailure) error("fixture local failure")
            return value
        }
        override suspend fun save(session: SavedSession, isCurrent: () -> Boolean): Boolean {
            saveGate?.await()
            if (!isCurrent()) return false
            if (saveFailure) error("fixture write failure")
            value = session
            return true
        }
        override fun clear(isCurrent: () -> Boolean) { if (isCurrent()) value = null }
    }

    private enum class Mode { SUCCESS, NETWORK, REVOKED, EXPIRED, EVALUATION, SERVER_ERROR, CHANGED_HTML, OTHER_IDENTITY, UNKNOWN_MOODLE }

    private class Cache : CourseCache {
        val values = mutableMapOf<CourseOwner, CachedCourses>()
        var readFailure: Exception? = null
        override suspend fun read(owner: CourseOwner): CachedCourses? {
            readFailure?.let { throw it }
            return values[owner]
        }
        override suspend fun replace(owner: CourseOwner, snapshot: CachedCourses, isCurrent: () -> Boolean): Boolean {
            if (!isCurrent()) return false
            values[owner] = snapshot
            return true
        }
        override suspend fun delete(owner: CourseOwner) { values.remove(owner) }
    }

    private class Fixture(val store: Store, val mode: Mode = Mode.SUCCESS, val cache: CourseCache = NoCourseCache,
        val diagnostics: AuthenticationDiagnostics = ConsoleAuthenticationDiagnostics,
        val storageDiagnostics: StorageDiagnostics = ConsoleStorageDiagnostics) {
        val paths = mutableListOf<String>()
        val receivedCookies = mutableListOf<String>()
        val httpClients = mutableListOf<HttpClient>()
        val session = AcademicSession(store, diagnostics = storageDiagnostics) { cookieStorage ->
            IntegrationClients(createHttpClient(MockEngine { request ->
                val path = request.url.encodedPath.ifEmpty { "/" }
                paths += path
                when {
                    path.endsWith("token.php") -> respond("""{"token":"fixture-token"}""", headers = jsonHeaders)
                    path.endsWith("server.php") -> {
                        if (mode == Mode.NETWORK) throw kotlinx.io.IOException("fixture network failure")
                        if (mode == Mode.UNKNOWN_MOODLE) respond("{}", headers = jsonHeaders)
                        else if (mode == Mode.REVOKED) respond("""{"exception":"fixture","errorcode":"invalidtoken"}""", headers = jsonHeaders)
                        else respond("""{"userid":${if (mode == Mode.OTHER_IDENTITY) 99 else 12},"username":"student","fullname":"Estudiante ficticio"}""", headers = jsonHeaders)
                    }
                    path == "/login" && request.method == HttpMethod.Get -> respond("""<input name="__RequestVerificationToken" value="fixture-verification">""")
                    path == "/login" -> respond("OK")
                    path == "/" -> respond("""<input name="code" value="fixture-code"><input name="state" value="fixture-state">""")
                    path == "/signin-oidc" -> respond("OK", headers = headersOf(HttpHeaders.SetCookie,
                        "session=fixture-cookie; Path=/; Secure; HttpOnly; Max-Age=3600"))
                    path == "/perfil" -> {
                        receivedCookies += request.headers[HttpHeaders.Cookie].orEmpty()
                        when (mode) {
                            Mode.EXPIRED -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://campus.unjbg.edu.pe/login"))
                            Mode.EVALUATION -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/alumno/evaluacion-docente"))
                            Mode.SERVER_ERROR -> respond("fixture", HttpStatusCode.ServiceUnavailable)
                            Mode.CHANGED_HTML -> respond("<h1>Unknown structure</h1>")
                            else -> respond("""<span class="m-card-profile__name">Estudiante ficticio</span>""")
                        }
                    }
                    else -> error("Unexpected fixture request")
                }
            }, cookieStorage).also(httpClients::add))
        }
        val auth = AuthRepositoryImpl(session, diagnostics, cache, storageDiagnostics)
        suspend fun login() = auth.signIn("student", "fixture-moodle-password", "fixture-intranet-password").getOrThrow()
        fun endProcess() = session.close()
    }

    @Test
    fun restartUsesSavedTokenAndCookiesWithoutPostingCredentials() = runTest {
        val store = Store()
        val first = Fixture(store)
        assertTrue(first.login().sessionRemembered)
        val json = AppJson.encodeToString(assertNotNull(store.value))
        store.value = AppJson.decodeFromString<SavedSession>(json)
        assertFalse(json.contains("fixture-moodle-password"))
        assertFalse(json.contains("fixture-intranet-password"))
        first.endProcess()
        val reopened = Fixture(store)
        try {
            val access = assertNotNull(reopened.auth.restoreSession().getOrThrow())
            assertTrue(access.sessionRemembered)
            assertTrue(access.intranetAvailable)
            assertEquals(listOf("/webservice/rest/server.php", "/perfil"), reopened.paths)
            assertTrue(reopened.receivedCookies.single().contains("session=fixture-cookie"))
        } finally { reopened.auth.signOut() }
    }

    @Test
    fun networkServerAndUnknownHtmlFailuresKeepSavedAccessForRetry() = runTest {
        for (mode in listOf(Mode.NETWORK, Mode.SERVER_ERROR, Mode.CHANGED_HTML, Mode.UNKNOWN_MOODLE)) {
            val store = Store()
            Fixture(store).apply { login(); endProcess() }
            val saved = store.value
            val reopened = Fixture(store, mode)
            val error = reopened.auth.restoreSession().exceptionOrNull() as SessionRecoveryException
            assertEquals(SessionRecoveryFailure.TEMPORARY, error.reason)
            assertEquals(saved, store.value)
            assertNull(reopened.session.authenticated())
            val retry = Fixture(store)
            try { assertNotNull(retry.auth.restoreSession().getOrThrow()) } finally { retry.auth.signOut() }
        }
    }

    @Test
    fun confirmedExpiryOrDifferentIdentityRequiresLoginAndErasesSavedSession() = runTest {
        for (mode in listOf(Mode.REVOKED, Mode.EXPIRED, Mode.OTHER_IDENTITY)) {
            val store = Store()
            Fixture(store).apply { login(); endProcess() }
            val reopened = Fixture(store, mode)
            val error = reopened.auth.restoreSession().exceptionOrNull() as SessionRecoveryException
            assertEquals(SessionRecoveryFailure.EXPIRED, error.reason)
            assertNull(store.value)
            assertNull(reopened.session.authenticated())
        }
    }

    @Test
    fun pendingEvaluationIsRestoredWithoutGrantingFullAccess() = runTest {
        val store = Store()
        Fixture(store).apply { login(); endProcess() }
        val reopened = Fixture(store, Mode.EVALUATION)
        try {
            val access = assertNotNull(reopened.auth.restoreSession().getOrThrow())
            assertFalse(access.intranetAvailable)
            assertEquals(IntranetRequirement.TEACHER_EVALUATION, access.intranetRequirement)
            assertTrue(reopened.session.authenticated()!!.clients.httpClient.cookies("https://intranet.unjbg.edu.pe/").isNotEmpty())
        } finally { reopened.auth.signOut() }
    }

    @Test
    fun logoutPreventsAnAlreadyStartedWriteFromRestoringSavedAccess() = runTest {
        val store = Store()
        Fixture(store).apply { login(); endProcess() }
        store.saveGate = CompletableDeferred()
        val reopened = Fixture(store)
        val restoring = async { reopened.auth.restoreSession() }
        runCurrent()
        reopened.auth.signOut()
        store.saveGate!!.complete(Unit)
        assertFailsWith<CancellationException> { restoring.await() }
        assertNull(store.value)
        assertNull(Fixture(store).auth.restoreSession().getOrThrow())
    }

    @Test
    fun localReadFailureIsRetryableAndWriteFailureDoesNotClaimAccessWasRemembered() = runTest {
        val store = Store()
        Fixture(store).apply { login(); endProcess() }
        store.loadFailure = true
        val reopened = Fixture(store)
        val error = reopened.auth.restoreSession().exceptionOrNull() as SessionRecoveryException
        assertEquals(SessionRecoveryFailure.STORAGE, error.reason)
        assertNotNull(store.value)
        store.loadFailure = false
        store.saveFailure = true
        try { assertFalse(reopened.login().sessionRemembered) } finally { reopened.auth.signOut() }
    }

    @Test
    fun logoutErasesAccessAndNextAccountStartsWithAnEmptyCookieJar() = runTest {
        val store = Store()
        val first = Fixture(store)
        first.login()
        first.auth.signOut()
        assertNull(store.value)
        assertNull(Fixture(store).auth.restoreSession().getOrThrow())
        assertTrue(first.session.begin().clients.httpClient.cookies("https://intranet.unjbg.edu.pe/").isEmpty())
        first.auth.signOut()
    }

    @Test
    fun offlineRecoveryRequiresSavedAccessAndOnlyReadsItsOwner() = runTest {
        val cache = Cache()
        val owner = CourseOwner(UNJBG_MOODLE_PROVIDER, 12)
        val other = CourseOwner(UNJBG_MOODLE_PROVIDER, 99)
        cache.values[owner] = CachedCourses(listOf(CourseSummary(1, "Cuenta A", "A")), 123)
        cache.values[other] = CachedCourses(listOf(CourseSummary(1, "Cuenta B", "B")), 456)
        val store = Store()
        Fixture(store).apply { login(); endProcess() }
        val reopened = Fixture(store, Mode.NETWORK, cache)
        val saved = store.value
        val access = assertNotNull(reopened.auth.restoreSession().getOrThrow())
        assertEquals(AccessMode.LOCAL_ONLY, access.mode)
        assertFalse(access.intranetAvailable)
        assertEquals(saved, store.value)
        val courses = org.basadrito.app.data.repository.CoursesRepositoryImpl(reopened.session, cache)
        assertEquals("Cuenta A", courses.getSnapshot().getOrThrow()!!.courses.single().name)
        assertEquals(1, reopened.paths.size) // Only restoration attempted HTTP; local reads do not.
        reopened.auth.signOut()
        assertNull(store.value)
        assertTrue(courses.getCourses().isFailure)
        assertEquals(2, cache.values.size)
        assertNull(Fixture(store, Mode.NETWORK, cache).auth.restoreSession().getOrThrow())
    }

    @Test
    fun cachedDataCannotBypassRevocationUnknownResponsesOrEvaluation() = runTest {
        val cache = Cache()
        cache.values[CourseOwner(UNJBG_MOODLE_PROVIDER, 12)] = CachedCourses(emptyList(), 123)
        for (mode in listOf(Mode.REVOKED, Mode.EXPIRED, Mode.OTHER_IDENTITY, Mode.CHANGED_HTML, Mode.UNKNOWN_MOODLE)) {
            val store = Store()
            Fixture(store).apply { login(); endProcess() }
            val reopened = Fixture(store, mode, cache)
            assertTrue(reopened.auth.restoreSession().isFailure)
            assertNull(reopened.session.authenticated())
            reopened.endProcess()
        }
        val store = Store()
        Fixture(store, Mode.EVALUATION).apply { login(); endProcess() }
        assertFalse(store.value!!.allowOfflineRead)
        assertTrue(Fixture(store, Mode.NETWORK, cache).auth.restoreSession().isFailure)
        assertEquals(1, cache.values.size)
    }

    @Test
    fun cacheFromAnotherAccountNeverUnlocksOfflineRecovery() = runTest {
        val cache = Cache()
        cache.values[CourseOwner(UNJBG_MOODLE_PROVIDER, 99)] = CachedCourses(emptyList(), 123)
        val store = Store()
        Fixture(store).apply { login(); endProcess() }
        val reopened = Fixture(store, Mode.NETWORK, cache)
        assertTrue(reopened.auth.restoreSession().isFailure)
        assertNull(reopened.session.authenticated())
        assertNotNull(store.value)
    }

    private companion object { val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json") }

    @Test fun offlineCacheFailureIsReportedAsStorageAndKeepsSavedAccessForRetry() = runTest {
        val store = Store()
        Fixture(store).apply { login(); endProcess() }
        val saved = store.value
        val cache = Cache().apply { readFailure = CourseCacheStorageException(kotlinx.io.IOException("fixture-secret")) }
        val events = mutableListOf<StorageDiagnostic>()
        val reopened = Fixture(store, Mode.NETWORK, cache, storageDiagnostics = StorageDiagnostics(events::add))
        val error = reopened.auth.restoreSession().exceptionOrNull() as SessionRecoveryException
        assertEquals(SessionRecoveryFailure.STORAGE, error.reason)
        assertEquals(saved, store.value)
        assertNull(reopened.session.authenticated())
        reopened.httpClients.single().coroutineContext[Job]!!.join()
        assertFalse(reopened.httpClients.single().coroutineContext[Job]!!.isActive)
        assertEquals(StorageOperation.READ_COURSES, events.single().operation)
        assertFalse(events.single().logLine().contains("fixture-secret"))
    }

    @Test fun diagnosticFailureDuringRestorationStillClosesTheProvisionalClient() = runTest {
        val store = Store()
        Fixture(store).apply { login(); endProcess() }
        val reopened = Fixture(store, Mode.NETWORK,
            diagnostics = AuthenticationDiagnostics { error("fixture diagnostic bug") })
        assertFailsWith<IllegalStateException> { reopened.auth.restoreSession() }
        reopened.httpClients.single().coroutineContext[Job]!!.join()
        assertFalse(reopened.httpClients.single().coroutineContext[Job]!!.isActive)
        assertNotNull(store.value)
    }
}
