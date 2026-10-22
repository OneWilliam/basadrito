package org.basadrito.app.data.intranet

import android.webkit.CookieManager
import io.ktor.client.plugins.cookies.cookies
import io.ktor.http.renderSetCookieHeader
import org.basadrito.app.data.http.apiCall
import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.domain.auth.PortalAccess
import org.basadrito.app.domain.auth.StudentIdentity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.cancellation.CancellationException

class AndroidIntranetEvaluationSession(private val session: AcademicSession) {
    suspend fun prepareBrowser(): Result<Unit> = try { apiCall {
        val connection = session.authenticated() ?: error("La sesión ya no está disponible.")
        val manager = CookieManager.getInstance()
        suspendCancellableCoroutine { continuation -> manager.removeAllCookies { if (continuation.isActive) continuation.resume(Unit) } }
        manager.setAcceptCookie(true)
        for (origin in listOf(IntranetLoginClient.DEFAULT_INTRANET_URL, IntranetLoginClient.DEFAULT_CAMPUS_URL)) {
            for (cookie in connection.clients.httpClient.cookies(origin)) {
                coroutineContext.ensureActive()
                val accepted = suspendCancellableCoroutine<Boolean> { continuation ->
                    manager.setCookie(origin, renderSetCookieHeader(cookie.copy(expires = null, maxAge = null))) {
                        if (continuation.isActive) continuation.resume(it)
                    }
                }
                check(accepted) { "No se pudo preparar el navegador de la aplicación." }
            }
        }
        check(session.isCurrent(connection)) { "La sesión cambió mientras se abría la evaluación." }
    }.onFailure { clearBrowser() }
    } catch (cancelled: CancellationException) {
        clearBrowser()
        throw cancelled
    }

    suspend fun confirmAccess(): Result<PortalAccess> = apiCall {
        val connection = session.authenticated() ?: error("La sesión ya no está disponible.")
        check(connection.clients.intranet.isAuthenticated()) {
            "La Intranet todavía no permite acceder al perfil. Completa las evaluaciones pendientes."
        }
        check(session.isCurrent(connection)) { "La sesión cambió durante la comprobación." }
        val moodle = connection.moodle ?: error("La sesión ya no está disponible.")
        PortalAccess(StudentIdentity(moodle.username, moodle.fullName), intranetAvailable = true,
            sessionRemembered = session.persist(connection))
    }

    fun clearBrowser() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
