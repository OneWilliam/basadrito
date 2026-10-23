package org.basadrito.app.data

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.basadrito.app.data.cache.*
import org.basadrito.app.data.di.IntegrationClients
import org.basadrito.app.data.http.createHttpClient
import org.basadrito.app.data.moodle.dto.MoodleSession
import org.basadrito.app.data.repository.*
import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.domain.academic.*
import org.basadrito.app.domain.auth.AccessMode
import org.basadrito.app.domain.calendar.*
import org.basadrito.app.domain.courses.*
import kotlin.test.*

class AcademicIntegrationTest {
    private class AcademicStore : AcademicCache {
        val data = MutableStateFlow<Map<CourseOwner, AcademicSnapshot>>(emptyMap())
        override fun observe(owner: CourseOwner) = data.map { it[owner] }
        override suspend fun read(owner: CourseOwner) = data.value[owner]
        override suspend fun replace(owner: CourseOwner, snapshot: AcademicSnapshot, isCurrent: () -> Boolean): Boolean {
            if (!isCurrent()) return false
            data.update { it + (owner to snapshot) }; return true
        }
    }
    private class AgendaStore : AgendaCache {
        val own = MutableStateFlow<Map<CourseOwner, List<PersonalItem>>>(emptyMap())
        val months = MutableStateFlow<Map<Pair<CourseOwner, CalendarMonth>, CalendarMonthData>>(emptyMap())
        override fun observeMonth(owner: CourseOwner, month: CalendarMonth) = months.map { it[owner to month] }
        override suspend fun replaceMonth(owner: CourseOwner, month: CalendarMonth, data: CalendarMonthData, isCurrent: () -> Boolean): Boolean {
            if (!isCurrent()) return false
            months.update { it + ((owner to month) to data) }; return true
        }
        override fun observePersonal(owner: CourseOwner) = own.map { it[owner].orEmpty() }
        override suspend fun savePersonal(owner: CourseOwner, item: PersonalItem, isCurrent: () -> Boolean) {
            check(isCurrent()); own.update { it + (owner to (it[owner].orEmpty().filterNot { row -> row.id == item.id } + item)) }
        }
        override suspend fun deletePersonal(owner: CourseOwner, id: String, isCurrent: () -> Boolean) {
            check(isCurrent()); own.update { it + (owner to it[owner].orEmpty().filterNot { row -> row.id == id }) }
        }
    }
    @Test fun providerDataReachesAcademicAndCalendarContractsAndPartialFailureKeepsCredits() = runTest {
        var rejectCredits = false
        var rejectAssignments = false
        var requests = 0
        val session = AcademicSession { IntegrationClients(createHttpClient(MockEngine { request ->
            requests++
            val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
            val function = parseQueryString(body)["wsfunction"]
            val path = request.url.encodedPath
            val json = when {
                function == "core_enrol_get_users_courses" -> """[{"id":1,"fullname":"19.00001 - Curso","shortname":"19.00001"}]"""
                function == "core_enrol_get_enrolled_users" -> """[{"id":5,"fullname":"Docente fixture","roles":[{"shortname":"editingteacher"}]}]"""
                function == "mod_assign_get_assignments" -> if (rejectAssignments) """{"courses":[],"warnings":[{"warningcode":"nopermissions"}]}"""
                    else """{"courses":[{"id":1,"assignments":[{"id":8,"name":"Entrega fixture","duedate":1792702800}]}]}"""
                function == "mod_assign_get_submission_status" -> """{"lastattempt":{"submission":{"status":"new"},"graded":false}}"""
                path.endsWith("malla-curricular") -> {
                    if (rejectCredits) return@MockEngine respond("unavailable", HttpStatusCode.ServiceUnavailable)
                    """<table><tr><td>Ciclo I</td><td></td></tr><tr><td>19.00001</td><td>Curso</td><td>4</td><td>2</td><td>0</td><td>2</td></tr></table>"""
                }
                path.contains("/progreso/nivel/") -> """[{"courseCode":"19.00001","courseName":"Curso","credits":"4","status":true}]"""
                path.endsWith("horario-semana/get") -> """[{"title":"19.00001 - Curso","description":"Aula 1","start":"2026-10-22T08:00:00","end":"2026-10-22T10:00:00"}]"""
                path.endsWith("inasistencias") -> """<select class="select-term"><option value="P1">Actual</option></select>"""
                path.contains("/inasistencias/periodo/") -> """[{"courseName":"19.00001 - Curso","dictated":10,"assisted":9}]"""
                else -> error("Unexpected fixture request")
            }
            respond(json, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })) }
        val cache = AcademicStore()
        val agenda = AgendaStore()
        val courses = CoursesRepositoryImpl(session)
        val academic = AcademicRepositoryImpl(session, courses, cache)
        val personal = PersonalAgendaRepositoryImpl(session, agenda)
        val calendar = CalendarRepositoryImpl(session, academic, personal, agenda)
        try {
            session.activate(session.begin(), MoodleSession("fixture", 12))
            academic.refresh()
            val first = academic.observe().first { it.snapshot.credits != null }
            assertEquals(CreditProgress(4.0, 4.0), first.snapshot.credits)
            assertEquals("Docente fixture", first.snapshot.teachers!![1])
            assertEquals(90.0, first.snapshot.attendance!!.single().percentage)
            assertEquals(1, first.snapshot.tasks!!.single().courseId)
            assertTrue(first.snapshot.tasks!!.single().pending!!)
            personal.save(PersonalItem("", "Mi tarea", kind = PersonalKind.TASK, date = "2026-10-22")).getOrThrow()
            val month = CalendarMonth(2026, 10)
            calendar.refresh(month)
            val unified = calendar.observe(month).first { it.events.any { event -> event.origin == CalendarOrigin.CLASS } }
            assertTrue(unified.events.any { it.origin == CalendarOrigin.MOODLE })
            assertTrue(unified.events.any { it.origin == CalendarOrigin.PERSONAL_TASK })
            rejectCredits = true
            rejectAssignments = true
            academic.refresh()
            val partial = academic.observe().first { AcademicSection.CREDITS in it.failedSections }
            assertEquals(first.snapshot.credits, partial.snapshot.credits)
            assertTrue(AcademicSection.TASKS in partial.failedSections)
            assertEquals(first.snapshot.tasks, partial.snapshot.tasks)
            val beforeOffline = requests
            val offline = session.begin()
            offline.mode = AccessMode.LOCAL_ONLY
            session.activate(offline, MoodleSession("fixture", 12))
            calendar.refresh(month)
            assertTrue(calendar.observe(month).first().events.isNotEmpty())
            assertEquals(beforeOffline, requests)
        } finally { session.close() }
    }
    @Test fun personalTasksAreEditableCompletableAndHiddenAfterLogoutOrAccountSwitch() = runTest {
        val session = AcademicSession { IntegrationClients(createHttpClient(MockEngine { error("No HTTP expected") })) }
        val store = AgendaStore()
        val repository = PersonalAgendaRepositoryImpl(session, store)
        try {
            session.activate(session.begin(), MoodleSession("A", 12))
            repository.save(PersonalItem("", "A", kind = PersonalKind.TASK)).getOrThrow()
            val task = repository.observe().first().single()
            assertTrue(task.id.isNotBlank())
            repository.save(task.copy(title = "Editada", completed = true)).getOrThrow()
            assertTrue(repository.observe().first().single().completed)
            session.clear()
            assertTrue(repository.observe().first().isEmpty())
            assertTrue(repository.save(task).isFailure)
            session.activate(session.begin(), MoodleSession("B", 99))
            assertTrue(repository.observe().first().isEmpty())
            repository.save(task.copy(title = "B")).getOrThrow()
            repository.delete(task.id).getOrThrow()
            assertTrue(repository.observe().first().isEmpty())
            assertEquals("Editada", store.own.value[CourseOwner(UNJBG_MOODLE_PROVIDER, 12)]!!.single().title)
        } finally { session.close() }
    }
}
