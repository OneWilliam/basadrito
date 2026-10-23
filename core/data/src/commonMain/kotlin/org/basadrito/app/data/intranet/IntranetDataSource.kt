package org.basadrito.app.data.intranet

import org.basadrito.app.data.intranet.dto.AcademicRecord
import org.basadrito.app.data.intranet.dto.AttendanceEntry
import org.basadrito.app.data.intranet.dto.CourseGradeDetail
import org.basadrito.app.data.intranet.dto.IntranetGrade
import org.basadrito.app.data.intranet.dto.MallaCourse
import org.basadrito.app.data.intranet.dto.PeriodOption
import org.basadrito.app.data.intranet.dto.ScheduleEntry
import org.basadrito.app.data.intranet.dto.StudentProfile
import org.basadrito.app.data.intranet.IntranetClient
import org.basadrito.app.data.intranet.IntranetLoginClient
import org.basadrito.app.data.http.apiCall
import org.basadrito.app.data.intranet.parser.GradeDetailParser
import org.basadrito.app.data.intranet.parser.IntranetHtmlParser
import org.basadrito.app.data.intranet.parser.IntranetJsonParser
import org.basadrito.app.data.intranet.format.isoWeekRange


class IntranetDataSource(
    private val auth: IntranetLoginClient,
    private val api: IntranetClient,
) {

    suspend fun login(username: String, password: String): Result<Unit> =
        auth.login(username, password)

    suspend fun isAuthenticated(): Boolean = auth.isAuthenticated()
    suspend fun checkAccess(): Result<Unit> = auth.checkAccess()

    suspend fun getProfile(): Result<StudentProfile> = apiCall {
        IntranetHtmlParser.parseProfile(api.getText(PATH_PROFILE).getOrThrow())
    }

    suspend fun getPeriods(): Result<List<PeriodOption>> = apiCall {
        IntranetHtmlParser.parsePeriods(api.getText(PATH_GRADES).getOrThrow())
    }

    suspend fun getSchedule(startIso: String, endIso: String): Result<List<ScheduleEntry>> = apiCall {
        val json = api.getText("$PATH_SCHEDULE?start=$startIso&end=$endIso").getOrThrow()
        IntranetJsonParser.parseSchedule(json)
    }

    suspend fun getWeekSchedule(todayIso: String): Result<List<ScheduleEntry>> = apiCall {
        val (monday, sunday) = isoWeekRange(todayIso)
        val json = api.getText("$PATH_SCHEDULE?start=$monday&end=$sunday").getOrThrow()
        IntranetJsonParser.parseSchedule(json)
    }

    suspend fun getGrades(periodId: String, periodName: String): Result<List<IntranetGrade>> = apiCall {
        val html = api.getText("$PATH_GRADES/periodo/$periodId/get").getOrThrow()
        IntranetHtmlParser.parseGrades(html, periodName)
    }

    suspend fun getGradeDetails(periodId: String): Result<List<CourseGradeDetail>> = apiCall {
        val html = api.getText("$PATH_GRADES/periodo/$periodId/get").getOrThrow()
        GradeDetailParser.parse(html)
    }

    suspend fun getMalla(): Result<List<MallaCourse>> = apiCall {
        IntranetHtmlParser.parseMalla(api.getText(PATH_MALLA).getOrThrow())
    }

    suspend fun getAcademicHistory(currentPeriodId: String? = null): Result<List<AcademicRecord>> = apiCall {
        val periods = IntranetHtmlParser.parsePeriods(api.getText(PATH_GRADES).getOrThrow())

        periods.flatMap { period ->
            val html = api.getText("$PATH_GRADES/periodo/${period.id}/get").getOrThrow()
            IntranetHtmlParser.parsePeriodRecords(html, period.name, isCurrent = currentPeriodId?.let { it == period.id })
        }
    }

    /** The caller supplies the selected period; the first option is not assumed to be current. */
    suspend fun getPeriodRecords(period: PeriodOption, isCurrent: Boolean? = null): Result<List<AcademicRecord>> = apiCall {
        require(period.id.isNotBlank()) { "Selecciona un período académico." }
        val html = api.getText("$PATH_GRADES/periodo/${period.id}/get").getOrThrow()
        IntranetHtmlParser.parsePeriodRecords(html, period.name, isCurrent)
    }

    suspend fun getAcademicProgress(cycles: IntRange): Result<List<AcademicRecord>> = apiCall {
        val records = mutableListOf<AcademicRecord>()
        for (cycle in cycles) {
            require(cycle > 0) { "Nivel académico inválido." }
            val json = api.getText("$PATH_PROGRESS/nivel/$cycle/get").getOrThrow()
            records += IntranetJsonParser.parseProgress(json, cycle)
        }
        records
    }

    suspend fun getAttendance(): Result<List<AttendanceEntry>> = apiCall {
        val page = api.getText(PATH_ATTENDANCE).getOrThrow()
        val periodId = IntranetHtmlParser.parseSelectedAttendancePeriod(page)

        if (periodId.isBlank()) {
            return@apiCall IntranetHtmlParser.parseAttendance(page)
        }

        val json = api.getText("$PATH_ATTENDANCE/periodo/$periodId/get").getOrThrow()
        IntranetJsonParser.parseAttendance(json)
    }

    suspend fun downloadSyllabus(
        studentSectionId: String,
        syllabusTeacherId: String,
    ): Result<ByteArray> = api.getBytes(
        "$PATH_GRADES/descargar-silabo?syllabusTeacherId=$syllabusTeacherId&studentSectionId=$studentSectionId"
    )

    private companion object {
        const val PATH_PROFILE = "/perfil"
        const val PATH_SCHEDULE = "/alumno/horario-semana/get"
        const val PATH_GRADES = "/alumno/notas"
        const val PATH_MALLA = "/alumno/malla-curricular"
        const val PATH_ATTENDANCE = "/alumno/inasistencias"
        const val PATH_PROGRESS = "/alumno/progreso"

    }
}
