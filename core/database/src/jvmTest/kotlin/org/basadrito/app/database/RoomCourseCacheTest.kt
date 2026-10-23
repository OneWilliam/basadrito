package org.basadrito.app.database

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import org.basadrito.app.data.cache.*
import org.basadrito.app.domain.courses.CourseSummary
import java.nio.file.Files
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

class RoomCourseCacheTest {
    @Test fun observationEmitsAtomicReplacementsEmptySnapshotAndSelectiveDelete() = runTest {
        val path = Files.createTempDirectory("basadrito-room-observe").resolve("academic.db").toString()
        val owner = CourseOwner("moodle:unjbg", 12)
        val other = CourseOwner("moodle:unjbg", 99)
        val cache = RoomCourseCache(openAcademicDatabase(path))
        val events = Channel<CachedCourses?>(Channel.UNLIMITED)
        val observation = backgroundScope.launch { cache.observe(owner).collect { events.send(it) } }
        try {
            assertNull(events.receive())
            val first = CachedCourses((1..30).map { CourseSummary(it, "Curso $it", "C$it") }, 123)
            cache.replace(owner, first) { true }
            assertEquals(first, events.receive())
            cache.replace(other, CachedCourses(listOf(CourseSummary(99, "Otra cuenta", "B")), 456)) { true }
            val empty = CachedCourses(emptyList(), 789)
            cache.replace(owner, empty) { true }
            assertEquals(empty, events.receive())
            cache.delete(owner)
            assertNull(events.receive())
            assertEquals("Otra cuenta", cache.read(other)!!.courses.single().name)
        } finally { observation.cancel(); cache.close() }
    }
    @Test fun sqliteFailureIsExposedAsStorageFailureInsteadOfAnAbsentRow() = runTest {
        val path = Files.createTempDirectory("basadrito-room-error-test").resolve("academic.db").toString()
        val owner = CourseOwner("moodle:unjbg", 12)
        val cache = RoomCourseCache(openAcademicDatabase(path))
        try {
            cache.replace(owner, CachedCourses(emptyList(), 123)) { true }
            BundledSQLiteDriver().open(path).use { connection ->
                connection.prepare("DROP TABLE courses").use { it.step() }
            }
            assertFailsWith<CourseCacheStorageException> { cache.read(owner) }
        } finally { cache.close() }
    }
    @Test fun accountsAndProvidersRemainSeparateAcrossReopenAndDelete() = runTest {
        val path = Files.createTempDirectory("basadrito-room-test").resolve("academic.db").toString()
        val first = CourseOwner("moodle:unjbg", 12)
        val second = CourseOwner("moodle:unjbg", 99)
        val otherSite = CourseOwner("moodle:other", 12)
        var cache = RoomCourseCache(openAcademicDatabase(path))
        try {
            for ((owner, name) in listOf(first to "A", second to "B", otherSite to "C")) {
                assertTrue(cache.replace(owner, CachedCourses(listOf(CourseSummary(1, name, name)), 123)) { true })
            }
            cache.close()
            cache = RoomCourseCache(openAcademicDatabase(path))
            assertEquals("A", cache.read(first)!!.courses.single().name)
            assertEquals("B", cache.read(second)!!.courses.single().name)
            assertEquals("C", cache.read(otherSite)!!.courses.single().name)
            cache.delete(first)
            assertNull(cache.read(first))
            assertEquals("B", cache.read(second)!!.courses.single().name)
        } finally { cache.close() }
    }

    @Test fun emptySnapshotIsValidAndAccountSwitchRollsBackWholeReplacement() = runTest {
        val path = Files.createTempDirectory("basadrito-room-guard-test").resolve("academic.db").toString()
        val owner = CourseOwner("moodle:unjbg", 12)
        val cache = RoomCourseCache(openAcademicDatabase(path))
        try {
            val old = CachedCourses(listOf(CourseSummary(1, "Anterior", "A")), 123)
            cache.replace(owner, old) { true }
            var checks = 0
            assertFailsWith<CancellationException> {
                cache.replace(owner, CachedCourses(emptyList(), 456)) { ++checks == 1 }
            }
            assertEquals(old, cache.read(owner))
            assertFalse(cache.replace(owner, CachedCourses(emptyList(), 456)) { false })
            assertEquals(old, cache.read(owner))
            assertTrue(cache.replace(owner, CachedCourses(emptyList(), 789)) { true })
            assertEquals(CachedCourses(emptyList(), 789), cache.read(owner))
        } finally { cache.close() }
    }
}
