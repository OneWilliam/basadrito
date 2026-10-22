package org.basadrito.app.data

import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import org.basadrito.app.data.session.SessionCookieStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionCookieStorageTest {
    @Test
    fun restartDoesNotResetMaxAgeAndSessionCookiesCanBeRecovered() = runTest {
        var clock = 1_000L
        val jar = SessionCookieStorage { clock }
        val origin = Url("https://intranet.unjbg.edu.pe/perfil")
        jar.addCookie(origin, Cookie("short", "fixture", maxAge = 10, path = "/", secure = true))
        jar.addCookie(origin, Cookie("session", "fixture", path = "/", secure = true, httpOnly = true))
        val saved = jar.withSnapshot { it }
        clock = 10_000L
        val reopened = SessionCookieStorage { clock }
        reopened.restore(saved)
        assertEquals(2, reopened.get(origin).size)
        clock = 11_001L
        assertEquals(listOf("session"), reopened.get(origin).map { it.name })
    }

    @Test
    fun cookiesKeepHostPathAndHttpsBoundariesAcrossRestart() = runTest {
        val jar = SessionCookieStorage()
        jar.addCookie(Url("https://intranet.unjbg.edu.pe/alumno/encuesta"), Cookie("scoped", "fixture", secure = true))
        jar.addCookie(Url("https://intranet.unjbg.edu.pe/"), Cookie("host-only", "fixture", path = "/"))
        jar.addCookie(Url("https://intranet.unjbg.edu.pe/"), Cookie("domain", "fixture", domain = ".unjbg.edu.pe", path = "/"))
        val reopened = SessionCookieStorage()
        reopened.restore(jar.withSnapshot { it })
        assertEquals(setOf("host-only", "domain"), reopened.get(Url("https://intranet.unjbg.edu.pe/perfil")).map { it.name }.toSet())
        assertEquals(setOf("scoped", "host-only", "domain"), reopened.get(Url("https://intranet.unjbg.edu.pe/alumno/otra")).map { it.name }.toSet())
        assertEquals(listOf("domain"), reopened.get(Url("https://campus.unjbg.edu.pe/")).map { it.name })
        assertTrue(reopened.get(Url("https://intranet.unjbg.edu.pe/alumnox/")).none { it.name == "scoped" })
        for (url in listOf("http://intranet.unjbg.edu.pe/", "https://evil.example/", "https://intranet.unjbg.edu.pe:444/")) {
            assertTrue(reopened.get(Url(url)).isEmpty())
        }
    }

    @Test
    fun deletionAndUnrelatedDomainsCannotLeaveUsableCookies() = runTest {
        val jar = SessionCookieStorage()
        val origin = Url("https://intranet.unjbg.edu.pe/")
        jar.addCookie(origin, Cookie("session", "first", path = "/"))
        jar.addCookie(origin, Cookie("session", "deleted", path = "/", maxAge = 0))
        jar.addCookie(origin, Cookie("poison", "fixture", domain = "evil.example", path = "/"))
        jar.addCookie(Url("https://evil.example/"), Cookie("poison", "fixture", domain = "unjbg.edu.pe", path = "/"))
        assertTrue(jar.get(origin).isEmpty())
        assertTrue(jar.withSnapshot { it }.isEmpty())
    }
}
