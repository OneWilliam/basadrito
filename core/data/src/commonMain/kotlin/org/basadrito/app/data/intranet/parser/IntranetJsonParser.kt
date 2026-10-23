package org.basadrito.app.data.intranet.parser

import org.basadrito.app.data.intranet.dto.AcademicRecord
import org.basadrito.app.data.intranet.dto.AttendanceDto
import org.basadrito.app.data.intranet.dto.AttendanceEntry
import org.basadrito.app.data.intranet.dto.ProgressDto
import org.basadrito.app.data.intranet.dto.ScheduleEntry
import org.basadrito.app.data.intranet.dto.ScheduleEventDto
import org.basadrito.app.data.http.AppJson
import org.basadrito.app.data.intranet.format.isoTimePart
import org.basadrito.app.data.intranet.format.weekdayNameEs


object IntranetJsonParser {

    fun parseSchedule(json: String): List<ScheduleEntry> = decodeList(json) { text ->
        AppJson.decodeFromString<List<ScheduleEventDto>>(text)
    }.map { dto ->
        ScheduleEntry(
            course = dto.title,
            detail = dto.description.cleanHtml(),
            day = weekdayNameEs(dto.start),
            startTime = isoTimePart(dto.start),
            endTime = isoTimePart(dto.end),
            startIso = dto.start,
            endIso = dto.end,
        )
    }

    fun parseAttendance(json: String): List<AttendanceEntry> = decodeList(json) { text ->
        AppJson.decodeFromString<List<AttendanceDto>>(text)
    }.map { dto ->
        AttendanceEntry(
            course = dto.courseName,
            totalClasses = dto.classCount,
            dictated = dto.dictated,
            attended = dto.assisted,
            absent = dto.absences,
            absencePercentage = dto.absencesPercentage,
        )
    }

    fun parseProgress(json: String, cycle: Int = 0): List<AcademicRecord> = decodeList(json) { text ->
        AppJson.decodeFromString<List<ProgressDto>>(text)
    }.map { dto ->
        AcademicRecord(
            courseName = dto.courseName,
            courseCode = dto.courseCode,
            course = dto.course,
            credits = dto.credits,
            attempt = dto.attempt,
            grade = dto.grade,
            term = dto.term,
            approved = dto.status,
            cycle = cycle,
        )
    }

    private fun <T> decodeList(json: String, decode: (String) -> List<T>): List<T> {
        val trimmed = json.trim()
        if (trimmed.isEmpty() || trimmed == "[]" || trimmed == "null") return emptyList()
        return decode(trimmed)
    }
}
