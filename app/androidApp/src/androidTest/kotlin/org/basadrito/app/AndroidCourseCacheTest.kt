package org.basadrito.app

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.basadrito.app.data.cache.*
import org.basadrito.app.database.RoomCourseCache
import org.basadrito.app.database.openAcademicDatabase
import org.basadrito.app.domain.courses.CourseSummary
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class AndroidCourseCacheTest {
    @Test fun roomReopensSnapshotsWithoutMixingAccounts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "cache-test-${UUID.randomUUID()}.db"
        val a = CourseOwner(UNJBG_MOODLE_PROVIDER, 12)
        val b = CourseOwner(UNJBG_MOODLE_PROVIDER, 99)
        var cache = RoomCourseCache(openAcademicDatabase(context, name))
        try {
            cache.replace(a, CachedCourses(listOf(CourseSummary(1, "Cuenta A", "A")), 123)) { true }
            cache.replace(b, CachedCourses(listOf(CourseSummary(1, "Cuenta B", "B")), 456)) { true }
            cache.close()
            cache = RoomCourseCache(openAcademicDatabase(context, name))
            assertEquals("Cuenta A", cache.read(a)!!.courses.single().name)
            assertEquals("Cuenta B", cache.read(b)!!.courses.single().name)
            cache.delete(a)
            assertNull(cache.read(a))
            assertNotNull(cache.read(b))
        } finally {
            cache.close()
            listOf("", "-wal", "-shm").forEach { File(context.noBackupFilesDir, name + it).delete() }
        }
    }
}
