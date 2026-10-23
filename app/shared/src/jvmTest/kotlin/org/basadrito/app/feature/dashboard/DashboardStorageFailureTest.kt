package org.basadrito.app.feature.dashboard

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.basadrito.app.domain.courses.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardStorageFailureTest {
    private class Courses : CoursesRepository {
        var local: Result<CourseSnapshot?> = Result.success(null)
        var remote: Result<CourseSnapshot?> = Result.success(null)
        var remoteGate: CompletableDeferred<Unit>? = null
        override suspend fun getCourses() = remote.map { it?.courses.orEmpty() }
        override suspend fun getSnapshot(refresh: Boolean): Result<CourseSnapshot?> {
            if (!refresh) return local
            remoteGate?.await()
            return remote
        }
    }

    @Test fun failedSaveKeepsCoursesVisibleAndSuccessfulRetryClearsWarning() = runTest {
        val fixture = Courses().apply {
            local = Result.failure(CoursesStorageException())
            remote = Result.success(CourseSnapshot(listOf(CourseSummary(1, "Curso", "C")), CourseSource.REMOTE,
                cacheWarning = CourseCacheWarning.WRITE_FAILED))
            remoteGate = CompletableDeferred()
        }
        val model = DashboardViewModel(fixture, backgroundScope)
        runCurrent()
        assertTrue(model.state.value.isLoading)
        assertEquals(CourseCacheWarning.READ_FAILED, model.state.value.cacheWarning)
        fixture.remoteGate!!.complete(Unit)
        runCurrent()
        assertEquals("Curso", model.state.value.courses.single().name)
        assertEquals(CourseCacheWarning.WRITE_FAILED, model.state.value.cacheWarning)
        assertNull(model.state.value.errorMessage)
        fixture.local = Result.success(null)
        fixture.remote = Result.success(CourseSnapshot(listOf(CourseSummary(1, "Curso", "C")), CourseSource.REMOTE))
        model.loadCourses()
        runCurrent()
        assertNull(model.state.value.cacheWarning)
        assertFalse(model.state.value.isLoading)
    }

    @Test fun unreadableCacheAndFailedRefreshDoNotBecomeAnEmptySuccess() = runTest {
        val fixture = Courses().apply {
            local = Result.failure(CoursesStorageException())
            remote = Result.failure(CoursesStorageException())
        }
        val model = DashboardViewModel(fixture, backgroundScope)
        runCurrent()
        assertEquals(CoursesStorageException().message, model.state.value.errorMessage)
        assertNull(model.state.value.source)
        assertFalse(model.state.value.isLoading)
    }
}
