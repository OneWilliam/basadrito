package org.basadrito.app.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.basadrito.app.data.cache.*
import org.basadrito.app.data.di.IntegrationClients
import org.basadrito.app.data.http.createHttpClient
import org.basadrito.app.data.moodle.dto.MoodleSession
import org.basadrito.app.data.repository.CoursesRepositoryImpl
import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.domain.courses.*
import kotlin.test.*

class CoursesRefreshTest {
    @Test fun simultaneousScreensShareRefreshAndOneCancelledWaiterDoesNotCancelTheOther() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        val session = AcademicSession { IntegrationClients(createHttpClient(MockEngine {
            requests++
            started.complete(Unit)
            release.await()
            respond("""[{"id":1,"fullname":"Curso","shortname":"C"}]""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })) }
        try {
            session.activate(session.begin(), MoodleSession("fixture", 12))
            val repository = CoursesRepositoryImpl(session)
            val first = async(start = CoroutineStart.UNDISPATCHED) { repository.refreshCourses(initial = true) }
            started.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { repository.refreshCourses(initial = true) }
            first.cancelAndJoin()
            assertTrue(second.isActive)
            release.complete(Unit)
            assertEquals("Curso", second.await().getOrThrow()!!.courses.single().name)
            assertEquals(1, requests)
            repository.refreshCourses(initial = true).getOrThrow()
            assertEquals(1, requests)
            repository.refreshCourses().getOrThrow()
            assertEquals(2, requests)
        } finally { release.complete(Unit); session.close() }
    }

    @Test fun observersFollowOnlyTheActiveOwnerAndLogoutRemovesVisibleData() = runTest {
        val stored = MutableStateFlow<Map<CourseOwner, CachedCourses>>(emptyMap())
        val cache = object : CourseCache {
            override fun observe(owner: CourseOwner) = stored.map { it[owner] }.distinctUntilChanged()
            override suspend fun read(owner: CourseOwner) = stored.value[owner]
            override suspend fun replace(owner: CourseOwner, snapshot: CachedCourses, isCurrent: () -> Boolean): Boolean {
                if (!isCurrent()) return false
                stored.update { it + (owner to snapshot) }
                return true
            }
            override suspend fun delete(owner: CourseOwner) { stored.update { it - owner } }
        }
        val session = AcademicSession { IntegrationClients(createHttpClient(MockEngine { error("No HTTP expected") })) }
        val first = CourseOwner(UNJBG_MOODLE_PROVIDER, 12)
        val second = CourseOwner(UNJBG_MOODLE_PROVIDER, 99)
        fun snapshot(name: String) = CachedCourses(listOf(CourseSummary(1, name, name)), 123)
        try {
            stored.value = mapOf(first to snapshot("A"), second to snapshot("B"))
            session.activate(session.begin(), MoodleSession("fixture-A", 12))
            val repository = CoursesRepositoryImpl(session, cache)
            val screens = List(2) { repository.observeCourses().stateIn(backgroundScope, SharingStarted.Eagerly, CourseFeed()) }
            for (screen in screens) assertEquals("A", screen.first { it.snapshot != null }.snapshot!!.courses.single().name)
            cache.replace(first, snapshot("A actualizada")) { true }
            for (screen in screens) assertEquals("A actualizada", screen.first {
                it.snapshot?.courses?.single()?.name == "A actualizada"
            }.snapshot!!.courses.single().name)
            session.clear()
            for (screen in screens) assertNull(screen.first { it.snapshot == null }.snapshot)
            session.activate(session.begin(), MoodleSession("fixture-B", 99))
            for (screen in screens) assertEquals("B", screen.first { it.snapshot != null }.snapshot!!.courses.single().name)
            assertEquals("A actualizada", cache.read(first)!!.courses.single().name)
        } finally { session.close() }
    }
}
