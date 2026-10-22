package org.basadrito.app.feature.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.basadrito.app.domain.auth.AuthRepository
import org.basadrito.app.domain.auth.PortalAccess
import org.basadrito.app.domain.auth.SessionRecoveryException
import org.basadrito.app.domain.auth.SessionRecoveryFailure
import org.basadrito.app.domain.auth.StudentIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionRecoveryViewModelTest {
    private class Auth : AuthRepository {
        var result: Result<PortalAccess?> = Result.success(null)
        var delayed: CompletableDeferred<Unit>? = null
        var logoutCount = 0
        override suspend fun restoreSession(): Result<PortalAccess?> {
            delayed?.let { withContext(NonCancellable) { it.await() } }
            return result
        }
        override suspend fun signIn(username: String, moodlePassword: String, intranetPassword: String): Result<PortalAccess> =
            Result.success(access)
        override fun signOut() { logoutCount++ }
    }

    @Test
    fun startupShowsRestorationThenPublishesSavedAccess() = runTest {
        val auth = Auth().apply { result = Result.success(access) }
        val model = AuthViewModel(auth, backgroundScope)
        assertTrue(model.state.value.isRestoring)
        assertTrue(model.state.value.isLoading)
        runCurrent()
        assertEquals(access, model.state.value.access)
        assertFalse(model.state.value.isRestoring)
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun noSavedSessionOpensLoginAndTemporaryFailureOffersRetry() = runTest {
        val auth = Auth()
        val model = AuthViewModel(auth, backgroundScope)
        runCurrent()
        assertFalse(model.state.value.isRestoring)
        auth.result = Result.failure(SessionRecoveryException(SessionRecoveryFailure.TEMPORARY))
        model.restoreSession()
        runCurrent()
        assertTrue(model.state.value.isRestoring)
        assertFalse(model.state.value.isLoading)
        assertNull(model.signIn())
        auth.result = Result.success(access)
        model.restoreSession()
        runCurrent()
        assertEquals(access, model.state.value.access)
    }

    @Test
    fun expiredAccessReturnsToLoginWithAnExplanation() = runTest {
        val auth = Auth().apply { result = Result.failure(SessionRecoveryException(SessionRecoveryFailure.EXPIRED)) }
        val model = AuthViewModel(auth, backgroundScope)
        runCurrent()
        assertFalse(model.state.value.isRestoring)
        assertNull(model.state.value.access)
        assertTrue(model.state.value.errorMessage!!.contains("Inicia sesión"))
    }

    @Test
    fun logoutDuringRestorationCannotBeUndoneByLateResult() = runTest {
        val gate = CompletableDeferred<Unit>()
        val auth = Auth().apply { result = Result.success(access); delayed = gate }
        val model = AuthViewModel(auth, backgroundScope)
        runCurrent()
        model.signOut()
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, auth.logoutCount)
        assertNull(model.state.value.access)
        assertFalse(model.state.value.isRestoring)
        assertFalse(model.state.value.isLoading)
    }

    private companion object {
        val access = PortalAccess(StudentIdentity("student", "Estudiante ficticio"), true, sessionRemembered = true)
    }
}
