package org.basadrito.app.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.basadrito.app.data.cache.*
import org.basadrito.app.data.di.IntegrationClients
import org.basadrito.app.data.diagnostics.*
import org.basadrito.app.data.http.createHttpClient
import org.basadrito.app.data.moodle.dto.MoodleSession
import org.basadrito.app.data.repository.CoursesRepositoryImpl
import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.domain.courses.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

class CourseCacheRepositoryTest {
    private class Cache : CourseCache {
        val values = mutableMapOf<CourseOwner, CachedCourses>()
        var readFailure: Exception? = null
        var writeFailure: Exception? = null
        var rejectWrite = false
        override suspend fun read(owner: CourseOwner): CachedCourses? {
            readFailure?.let { throw it }
            return values[owner]
        }
        override suspend fun replace(owner: CourseOwner, snapshot: CachedCourses, isCurrent: () -> Boolean): Boolean {
            if (!isCurrent()) return false
            writeFailure?.let { throw it }
            if (rejectWrite) return false
            values[owner] = snapshot
            return true
        }
        override suspend fun delete(owner: CourseOwner) { values.remove(owner) }
    }

    private fun remoteSession(unavailable: Boolean = false) = AcademicSession {
        IntegrationClients(createHttpClient(MockEngine {
            if (unavailable) respond("unavailable", HttpStatusCode.ServiceUnavailable)
            else respond("""[{"id":1,"fullname":"Curso descargado","shortname":"C"}]""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }))
    }

    @Test fun failedOrRejectedSaveKeepsRemoteDataAndReportsItWasNotSaved() = runTest {
        for (throwsStorageError in listOf(true, false)) {
            val cache = Cache().apply {
                if (throwsStorageError) writeFailure = CourseCacheStorageException(IllegalStateException("fixture-token-secret"))
                else rejectWrite = true
            }
            val events = mutableListOf<StorageDiagnostic>()
            val session = remoteSession()
            try {
                session.activate(session.begin(), MoodleSession("fixture", 12))
                val result = CoursesRepositoryImpl(session, cache, StorageDiagnostics(events::add)).getSnapshot().getOrThrow()!!
                assertEquals("Curso descargado", result.courses.single().name)
                assertEquals(CourseSource.REMOTE, result.source)
                assertEquals(CourseCacheWarning.WRITE_FAILED, result.cacheWarning)
                assertTrue(cache.values.isEmpty())
                assertEquals(if (throwsStorageError) listOf(StorageOperation.WRITE_COURSES) else emptyList(), events.map { it.operation })
                assertFalse(events.joinToString { it.logLine() }.contains("fixture-token-secret"))
            } finally { session.clear() }
        }
    }

    @Test fun readFailureIsNotAnAbsentSnapshotAndOnlineRecoveryKeepsItsWarning() = runTest {
        val cache = Cache().apply { readFailure = CourseCacheStorageException(kotlinx.io.IOException("fixture-path-secret")) }
        val events = mutableListOf<StorageDiagnostic>()
        val session = remoteSession()
        try {
            session.activate(session.begin(), MoodleSession("fixture", 12))
            val repository = CoursesRepositoryImpl(session, cache, StorageDiagnostics(events::add))
            assertIs<CoursesStorageException>(repository.getSnapshot(refresh = false).exceptionOrNull())
            val result = repository.getSnapshot().getOrThrow()!!
            assertEquals(CourseSource.REMOTE, result.source)
            assertEquals(CourseCacheWarning.READ_FAILED, result.cacheWarning)
            assertEquals(2, events.size)
            assertFalse(events.joinToString { it.logLine() }.contains("fixture-path-secret"))
        } finally { session.clear() }
    }

    @Test fun failedLocalAndRemoteReadsReturnStorageFailureInsteadOfAnEmptySuccess() = runTest {
        val cache = Cache().apply { readFailure = CourseCacheStorageException(kotlinx.io.IOException("fixture")) }
        val session = remoteSession(unavailable = true)
        try {
            session.activate(session.begin(), MoodleSession("fixture", 12))
            assertIs<CoursesStorageException>(CoursesRepositoryImpl(session, cache).getSnapshot().exceptionOrNull())
        } finally { session.clear() }
    }

    @Test fun unexpectedCacheErrorsPropagateInsteadOfBeingTreatedAsEmpty() = runTest {
        val cache = Cache().apply { readFailure = IllegalArgumentException("fixture programming error") }
        val session = remoteSession()
        try {
            session.activate(session.begin(), MoodleSession("fixture", 12))
            assertFailsWith<IllegalArgumentException> { CoursesRepositoryImpl(session, cache).getSnapshot() }
        } finally { session.clear() }
    }

    @Test fun cancelledStorageOperationsPropagateWithoutAnErrorDiagnostic() = runTest {
        for (reading in listOf(true, false)) {
            val cache = Cache().apply {
                if (reading) readFailure = CancellationException("fixture")
                else writeFailure = CancellationException("fixture")
            }
            val events = mutableListOf<StorageDiagnostic>()
            val session = remoteSession()
            try {
                session.activate(session.begin(), MoodleSession("fixture", 12))
                assertFailsWith<CancellationException> {
                    CoursesRepositoryImpl(session, cache, StorageDiagnostics(events::add)).getSnapshot()
                }
                assertTrue(events.isEmpty())
            } finally { session.clear() }
        }
    }

    @Test fun remoteFailurePreservesOnlyTheActiveAccountsCachedCourses() = runTest {
        val cache = Cache()
        val owner = CourseOwner(UNJBG_MOODLE_PROVIDER, 12)
        val other = CourseOwner(UNJBG_MOODLE_PROVIDER, 99)
        val snapshot = CachedCourses(listOf(CourseSummary(1, "Cuenta A", "A")), 123)
        cache.values[owner] = snapshot
        cache.values[other] = CachedCourses(listOf(CourseSummary(1, "Cuenta B", "B")), 456)
        val session = AcademicSession { IntegrationClients(createHttpClient(MockEngine {
            respond("unavailable", HttpStatusCode.ServiceUnavailable)
        })) }
        try {
            session.activate(session.begin(), MoodleSession("fixture", 12))
            val result = CoursesRepositoryImpl(session, cache).getSnapshot().getOrThrow()!!
            assertEquals(snapshot.courses, result.courses)
            assertEquals(CourseSource.CACHE, result.source)
            assertTrue(result.refreshFailed)
            assertEquals(snapshot, cache.values[owner])
            session.activate(session.begin(), MoodleSession("fixture-new", 100))
            assertTrue(CoursesRepositoryImpl(session, cache).getCourses().isFailure)
        } finally { session.clear() }
    }

    @Test fun responseFromAnOldSessionCannotReplaceEitherAccountsSnapshot() = runTest {
        val cache = Cache()
        val owner = CourseOwner(UNJBG_MOODLE_PROVIDER, 12)
        val other = CourseOwner(UNJBG_MOODLE_PROVIDER, 99)
        val previous = CachedCourses(listOf(CourseSummary(1, "Anterior", "A")), 123)
        val next = CachedCourses(listOf(CourseSummary(1, "Otra cuenta", "B")), 456)
        cache.values[owner] = previous
        cache.values[other] = next
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val session = AcademicSession { IntegrationClients(createHttpClient(MockEngine {
            started.complete(Unit)
            release.await()
            respond("""[{"id":1,"fullname":"Respuesta tardía","shortname":"T"}]""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })) }
        try {
            session.activate(session.begin(), MoodleSession("fixture", 12))
            val request = async { CoursesRepositoryImpl(session, cache).getCourses() }
            started.await()
            session.activate(session.begin(), MoodleSession("fixture-new", 99))
            release.complete(Unit)
            assertFailsWith<CancellationException> { request.await() }
            assertEquals(previous, cache.values[owner])
            assertEquals(next, cache.values[other])
        } finally { release.complete(Unit); session.clear() }
    }
}
