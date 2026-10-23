package org.basadrito.app.data.session

import io.ktor.http.Cookie
import kotlinx.serialization.Serializable
import org.basadrito.app.data.moodle.dto.MoodleSession

/** Secret material stays inside data. Implementations must encrypt it at rest. */
interface SessionStore {
    suspend fun load(): SavedSession?

    /** Check the guard inside the same critical section as the atomic write/clear. */
    suspend fun save(session: SavedSession, isCurrent: () -> Boolean): Boolean
    fun clear(isCurrent: () -> Boolean = { true })
}

/** Platforms without a protected store continue to authenticate in memory. */
object NoSessionStore : SessionStore {
    override suspend fun load(): SavedSession? = null
    override suspend fun save(session: SavedSession, isCurrent: () -> Boolean) = false
    override fun clear(isCurrent: () -> Boolean) = Unit
}

@Serializable
data class SavedSession(
    val moodle: MoodleSession,
    val cookies: List<SavedCookie>,
    val version: Int = 1,
    val allowOfflineRead: Boolean = false,
) {
    override fun toString() = "SavedSession(<redacted>)"
}

@Serializable
data class SavedCookie(val cookie: Cookie, val originHost: String, val hostOnly: Boolean) {
    override fun toString() = "SavedCookie(<redacted>)"
}
