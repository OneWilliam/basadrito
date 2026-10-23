package org.basadrito.app.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.basadrito.app.data.cache.*
import org.basadrito.app.domain.academic.*
import org.basadrito.app.domain.calendar.*
import java.nio.file.Files
import kotlin.test.*

class AcademicMigrationTest {
    @Test fun versionOneMigratesWithoutLosingCoursesAndSupportsNewAccountScopedData() = runTest {
        val path = Files.createTempDirectory("basadrito-migration").resolve("academic.db").toString()
        // Frozen schema v1 fixture, independent of the current entities and generated migration.
        BundledSQLiteDriver().open(path).use {
            it.execSQL("CREATE TABLE course_snapshots (provider TEXT NOT NULL, ownerId INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, PRIMARY KEY(provider,ownerId))")
            it.execSQL("CREATE TABLE courses (provider TEXT NOT NULL, ownerId INTEGER NOT NULL, courseId INTEGER NOT NULL, name TEXT NOT NULL, shortName TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(provider,ownerId,courseId))")
            it.execSQL("INSERT INTO course_snapshots VALUES ('moodle:unjbg',12,1800000000123)")
            it.execSQL("INSERT INTO courses VALUES ('moodle:unjbg',12,1,'Conservado','C',0)")
            it.execSQL("PRAGMA user_version=1")
        }
        val database = openAcademicDatabase(path)
        val owner = CourseOwner(UNJBG_MOODLE_PROVIDER, 12)
        try {
            val cache = RoomCourseCache(database)
            assertEquals("Conservado", cache.read(owner)!!.courses.single().name)
            assertEquals(1800000000123L, cache.read(owner)!!.updatedAtMillis)
            assertNull(cache.read(owner)!!.courses.single().startsAtEpochSeconds)
            val academic = RoomAcademicCache(database)
            val data = AcademicSnapshot(credits = CreditProgress(3.5, 12.5), tasks = emptyList(),
                updatedAtMillis = mapOf(AcademicSection.CREDITS to 1800000000123L, AcademicSection.TASKS to 1800000000123L))
            assertTrue(academic.replace(owner, data) { true })
            assertEquals(data, academic.observe(owner).first())
            assertNull(academic.read(CourseOwner(UNJBG_MOODLE_PROVIDER, 99)))
            val agenda = RoomAgendaCache(database)
            val task = PersonalItem("same-id", "Mi tarea", kind = PersonalKind.TASK, date = "2026-10-22")
            agenda.savePersonal(owner, task) { true }
            val other = CourseOwner(UNJBG_MOODLE_PROVIDER, 99)
            agenda.savePersonal(other, task.copy(title = "Otra cuenta")) { true }
            agenda.savePersonal(owner, task.copy(completed = true)) { true }
            assertTrue(agenda.observePersonal(owner).first().single().completed)
            agenda.deletePersonal(owner, task.id) { true }
            assertTrue(agenda.observePersonal(owner).first().isEmpty())
            assertEquals("Otra cuenta", agenda.observePersonal(other).first().single().title)
            val month = CalendarMonth(2026, 10)
            agenda.replaceMonth(owner, month, CalendarMonthData(emptyList(), 1800000000123L)) { true }
            assertNotNull(agenda.observeMonth(owner, month).first())
            assertNull(agenda.observeMonth(other, month).first())
        } finally { database.close() }
        val reopened = openAcademicDatabase(path)
        try {
            assertEquals(CreditProgress(3.5, 12.5), RoomAcademicCache(reopened).read(owner)!!.credits)
            assertEquals("Otra cuenta", RoomAgendaCache(reopened).observePersonal(CourseOwner(UNJBG_MOODLE_PROVIDER, 99)).first().single().title)
            assertEquals(1800000000123L, RoomAgendaCache(reopened).observeMonth(owner, CalendarMonth(2026, 10)).first()!!.updatedAtMillis)
        } finally { reopened.close() }
    }
    @Test fun failedAccountGuardRollsBackAcademicAndPersonalChanges() = runTest {
        val database = openAcademicDatabase(Files.createTempDirectory("basadrito-guards").resolve("db").toString())
        val owner = CourseOwner(UNJBG_MOODLE_PROVIDER, 12)
        try {
            val cache = RoomAcademicCache(database)
            val data = AcademicSnapshot(credits = CreditProgress(3.0, 10.0), updatedAtMillis = mapOf(AcademicSection.CREDITS to 100L))
            cache.replace(owner, data) { true }
            var checks = 0
            assertFailsWith<kotlin.coroutines.cancellation.CancellationException> {
                cache.replace(owner, data.copy(credits = CreditProgress(5.0, 10.0))) { ++checks == 1 }
            }
            assertEquals(data, cache.read(owner))
            val agenda = RoomAgendaCache(database)
            checks = 0
            assertFailsWith<kotlin.coroutines.cancellation.CancellationException> {
                agenda.savePersonal(owner, PersonalItem("x", "No guardar", kind = PersonalKind.TASK)) { ++checks == 1 }
            }
            assertTrue(agenda.observePersonal(owner).first().isEmpty())
        } finally { database.close() }
    }
}
