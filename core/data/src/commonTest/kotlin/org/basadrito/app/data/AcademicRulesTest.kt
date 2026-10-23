package org.basadrito.app.data

import org.basadrito.app.data.intranet.dto.*
import org.basadrito.app.data.intranet.parser.*
import org.basadrito.app.data.repository.calculateCredits
import org.basadrito.app.domain.calendar.*
import org.basadrito.app.domain.academic.*
import kotlin.test.*

class AcademicRulesTest {
    @Test fun repeatedAttemptsAndRepeatedPlanRowsDoNotDuplicateApprovedCredits() {
        val plan = listOf(MallaCourse(code = "A", credits = "3,5"), MallaCourse(code = "A", credits = "3,5"), MallaCourse(code = "B", credits = "4"))
        val records = listOf(AcademicRecord(courseCode = "A", approved = false), AcademicRecord(courseCode = "A", approved = true),
            AcademicRecord(courseCode = "A", approved = true), AcademicRecord(courseCode = "B", approved = false))
        assertEquals(CreditProgress(3.5, 7.5), calculateCredits(plan, records))
        assertFailsWith<IllegalArgumentException> { calculateCredits(plan, listOf(AcademicRecord(courseCode = "A", approved = null))) }
        assertFailsWith<IllegalArgumentException> { calculateCredits(listOf(MallaCourse(code = "A", credits = "?")), records) }
    }
    @Test fun absentSubmissionOrAttendanceIsNotZeroOrCompleted() {
        assertNull(CourseAttendance("A", 0, 0).percentage)
        assertEquals(80.0, CourseAttendance("A", 8, 10).percentage)
        assertNull(AcademicTask(1, 1, "T", null, null, SubmissionState.UNKNOWN, null).pending)
        assertFalse(AcademicTask(1, 1, "T", null, null, SubmissionState.SUBMITTED, false).pending!!)
    }
    @Test fun civilDatesAndMonthsRespectLeapYearsAndInstitutionTimezone() {
        assertEquals(29, CalendarMonth(2024, 2).days)
        assertEquals(28, CalendarMonth(2026, 2).days)
        assertEquals(CalendarMonth(2027, 1), CalendarMonth(2026, 12).shift(1))
        assertEquals(3, CalendarMonth(2026, 10).mondayOffset)
        assertFalse(validCivilDate("2026-02-30"))
        assertEquals("2026-10-21", institutionDate(kotlin.time.Instant.parse("2026-10-22T03:00:00Z").epochSeconds))
        PersonalItem("", "Sin fecha", kind = PersonalKind.TASK).validate()
        assertFailsWith<IllegalArgumentException> { PersonalItem("", "Evento", kind = PersonalKind.EVENT).validate() }
    }
    @Test fun migratedProfileAndScheduleKeepPreviouslyAvailableFields() {
        val profile = IntranetHtmlParser.parseProfile("""<table><tr><th>Nombre:</th><td>Alumno</td></tr><tr><th>Código:</th><td>C01</td></tr><tr><th>Carrera:</th><td>Sistemas</td></tr><tr><th>Correo:</th><td>fixture@example.test</td></tr></table>""")
        assertEquals("C01", profile.code)
        assertEquals("Sistemas", profile.career)
        assertEquals("fixture@example.test", profile.email)
        val schedule = IntranetJsonParser.parseSchedule("""[{"title":"Curso","description":"<b>Aula 1</b>","start":"2026-10-22T08:00:00","end":"2026-10-22T10:00:00"}]""").single()
        assertEquals("Aula 1", schedule.detail)
        assertEquals("Jueves", schedule.day)
    }
}
