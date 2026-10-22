package org.basadrito.app.data.session

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.matches
import io.ktor.http.Cookie
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Account-scoped jar. Max-Age becomes an absolute expiry, so a restart never renews it. */
internal class SessionCookieStorage(private val now: () -> Long = { GMTDate().timestamp }) : CookiesStorage {
    private val mutex = Mutex()
    private val cookies = mutableListOf<SavedCookie>()
    var onChanged: suspend (List<SavedCookie>) -> Unit = {}

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        removeExpired()
        if (!allowedOrigin(requestUrl)) return@withLock emptyList()
        cookies.filter { (!it.hostOnly || it.originHost == requestUrl.host.lowercase()) && it.cookie.matches(requestUrl) }
            .sortedByDescending { it.cookie.path?.length ?: 0 }.map { it.cookie }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) = mutex.withLock {
        if (!allowedOrigin(requestUrl) || cookie.name.isBlank()) return@withLock
        val host = requestUrl.host.lowercase()
        val domain = cookie.domain?.trimStart('.')?.lowercase()?.takeIf { it.isNotBlank() } ?: host
        if (domain !in DOMAINS || host != domain && !host.endsWith(".$domain")) return@withLock
        val path = cookie.path?.takeIf { it.startsWith('/') }
            ?: requestUrl.encodedPath.substringBeforeLast('/', "").ifEmpty { "/" }
        val expiry = cookie.maxAge?.let { now() + it.toLong() * 1_000 } ?: cookie.expires?.timestamp
        val normalized = cookie.copy(domain = domain, path = path, maxAge = null, expires = expiry?.let { GMTDate(it) })
        cookies.removeAll { it.cookie.name == normalized.name && it.cookie.domain == domain && it.cookie.path == path }
        if (expiry == null || expiry > now()) cookies += SavedCookie(normalized, host, cookie.domain.isNullOrBlank())
        removeExpired()
        // Serialize snapshots while holding the jar lock: older responses cannot overwrite newer cookies.
        onChanged(cookies.toList())
    }

    suspend fun restore(saved: List<SavedCookie>) = mutex.withLock {
        cookies.clear()
        cookies += saved.filter { entry ->
            val domain = entry.cookie.domain ?: return@filter false
            entry.originHost in HOSTS && domain in DOMAINS &&
                (entry.originHost == domain || entry.originHost.endsWith(".$domain")) &&
                entry.cookie.path?.startsWith('/') == true && entry.cookie.maxAge == null &&
                entry.cookie.name.isNotBlank() && (!entry.hostOnly || domain == entry.originHost)
        }
        removeExpired()
    }

    suspend fun <T> withSnapshot(block: suspend (List<SavedCookie>) -> T): T = mutex.withLock {
        removeExpired()
        block(cookies.toList())
    }

    private fun removeExpired() { cookies.removeAll { (it.cookie.expires?.timestamp ?: Long.MAX_VALUE) <= now() } }
    override fun close() = Unit

    private fun allowedOrigin(url: Url) = url.protocol == URLProtocol.HTTPS && url.port == 443 &&
        url.user == null && url.password == null && url.host.lowercase() in HOSTS

    companion object {
        private val HOSTS = setOf("campus.unjbg.edu.pe", "intranet.unjbg.edu.pe", "aula.unjbg.edu.pe")
        private val DOMAINS = HOSTS + "unjbg.edu.pe"
    }
}
