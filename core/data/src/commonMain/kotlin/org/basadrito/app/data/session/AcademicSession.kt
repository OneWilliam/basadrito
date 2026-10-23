package org.basadrito.app.data.session

import io.ktor.client.plugins.cookies.CookiesStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import org.basadrito.app.data.di.IntegrationClients
import org.basadrito.app.data.http.createHttpClient
import org.basadrito.app.data.moodle.dto.MoodleSession
import org.basadrito.app.domain.auth.AccessMode
import org.basadrito.app.data.diagnostics.*
import kotlin.coroutines.cancellation.CancellationException



class AcademicSession(
    private val store: SessionStore = NoSessionStore,
    private val diagnostics: StorageDiagnostics = ConsoleStorageDiagnostics,
    private val createClients: (CookiesStorage) -> IntegrationClients = { IntegrationClients(createHttpClient(it)) },
) {
    internal class Connection(
        val clients: IntegrationClients,
        val cookies: SessionCookieStorage,
        var moodle: MoodleSession? = null,
        var mode: AccessMode = AccessMode.VERIFIED,
        var allowOfflineRead: Boolean = false,
    )

    private val connection = MutableStateFlow<Connection?>(null)

    internal fun begin(): Connection {
        clear()
        return newConnection()
    }

    internal fun beginRestoration(): Connection {
        releaseMemory()
        return newConnection()
    }

    private fun newConnection(): Connection {
        val cookies = SessionCookieStorage()
        val candidate = Connection(createClients(cookies), cookies)
        cookies.onChanged = { snapshot -> persist(candidate, snapshot) }
        connection.value = candidate
        return candidate
    }

    internal fun activate(candidate: Connection, moodle: MoodleSession): Boolean {
        if (!isCurrent(candidate)) {
            return false
        }

        candidate.moodle = moodle
        if (!isCurrent(candidate)) {
            candidate.moodle = null
            return false
        }
        return true
    }
    internal fun authenticated(): Connection? = connection.value?.takeIf { it.moodle != null }

    internal fun isCurrent(candidate: Connection): Boolean {
        return connection.value === candidate
    }

    internal suspend fun saved(candidate: Connection): SavedSession? = store.load().also { requireCurrent(candidate) }

    internal suspend fun persist(candidate: Connection): Boolean = candidate.cookies.withSnapshot { persist(candidate, it) }

    private suspend fun persist(candidate: Connection, cookies: List<SavedCookie>): Boolean {
        if (candidate.mode == AccessMode.LOCAL_ONLY) return false
        val moodle = candidate.moodle ?: return false
        if (!isCurrent(candidate)) return false
        return try {
            store.save(SavedSession(moodle, cookies, allowOfflineRead = candidate.allowOfflineRead)) {
                isCurrent(candidate) && candidate.moodle != null
            } && isCurrent(candidate)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            diagnostics.report(StorageOperation.SAVE_SESSION, error)
            false // The login result explicitly marks that access was not remembered.
        }
    }

    internal fun requireCurrent(candidate: Connection) {
        if (!isCurrent(candidate)) throw CancellationException("La sesión cambió.")
    }

    internal fun forgetSaved(candidate: Connection) { store.clear { isCurrent(candidate) } }

    internal fun discard(candidate: Connection) {
        if (connection.compareAndSet(candidate, null)) {
            close(candidate)
        }
    }
    internal fun clear() {
        releaseMemory()
        store.clear()
    }

    private fun releaseMemory() { connection.getAndUpdate { null }?.let(::close) }

    /** Release process resources without deleting saved access; only signOut clears the store. */
    fun close() = releaseMemory()

    private fun close(candidate: Connection) {
        candidate.moodle = null
        candidate.clients.close()
    }
}
