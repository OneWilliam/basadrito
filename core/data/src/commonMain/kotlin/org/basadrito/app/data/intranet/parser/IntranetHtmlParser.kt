package org.basadrito.app.data.intranet.parser

import org.basadrito.app.data.intranet.dto.AcademicRecord
import org.basadrito.app.data.intranet.dto.AttendanceEntry
import org.basadrito.app.data.intranet.dto.IntranetGrade
import org.basadrito.app.data.intranet.dto.MallaCourse
import org.basadrito.app.data.intranet.dto.PeriodOption
import org.basadrito.app.data.intranet.dto.StudentProfile
import org.basadrito.app.data.intranet.format.toDoubleFlexible


object IntranetHtmlParser {

    fun parseProfile(html: String): StudentProfile {
        fun field(label: String) = Regex("$label[^<]*</t[dh]>\\s*<td[^>]*>([\\s\\S]*?)</td>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.cleanHtml().orEmpty()
        return StudentProfile(fullName = profileName(html).ifBlank { field("Nombre(?:s)?") },
            code = field("C[oó]dig[^<]*"), career = field("Carrera[^<]*"), email = field("Correo[^<]*"),
            phone = field("Tel[eé][^<]*"), address = field("Direcci[oó][^<]*"))
    }

    fun parsePeriods(html: String): List<PeriodOption> =
        Regex("""<option[^>]*value="([^"]*)"[^>]*>([^<]*)</option>""")
            .findAll(html)
            .map { PeriodOption(id = it.groupValues[1], name = it.groupValues[2].trim()) }
            .filter { it.id.isNotBlank() }
            .toList()

    fun parseSelectedAttendancePeriod(html: String): String =
        Regex("""select-term[\s\S]*?value="([^"]*)"""")
            .find(html)?.groupValues?.get(1) ?: ""

    /** Notas por curso de un periodo, con sus componentes EC/ED/EP. */
    fun parseGrades(html: String, periodName: String): List<IntranetGrade> {
        val grades = mutableListOf<IntranetGrade>()

        val courseMatches = COURSE_ANCHOR.findAll(html).toList()

        for (i in courseMatches.indices) {
            val name = courseMatches[i].groupValues[1].cleanHtml()
            val nextIndex = if (i + 1 < courseMatches.size) {
                html.indexOf("Curso:", courseMatches[i].range.last + 1).takeIf { it >= 0 } ?: html.length
            } else {
                html.length
            }
            val section = html.substring(courseMatches[i].range.last, nextIndex)

            val sectionName = fieldIn(section, "Secci.n")
            val credits = fieldIn(section, "Cr.ditos")

            var unitGrade = "-"
            val unitRow = UNIT_ROW.find(section)
            if (unitRow != null) {
                val rowEnd = section.indexOf("</tr>", unitRow.range.first).takeIf { it >= 0 } ?: section.length
                val cells = extractCells(section.substring(unitRow.range.first, rowEnd))
                if (cells.size >= 3) unitGrade = extractNumber(cells[2])
            }

            var ec = "-"
            var ed = "-"
            var ep = "-"
            for (row in GRADES_ROW.findAll(section)) {
                val cells = extractCells(row.groupValues[1])
                if (cells.size < 3) continue
                val label = cells[0]
                val grade = extractNumber(cells[2]).replace(PUBLISHED_SUFFIX, "").trim()
                when {
                    label.contains("EC") && ec == "-" -> ec = grade
                    label.contains("ED") && ed == "-" -> ed = grade
                    label.contains("EP") && ep == "-" -> ep = grade
                }
            }

            grades += IntranetGrade(
                course = name,
                section = sectionName,
                credits = credits,
                period = periodName,
                unitGrade = unitGrade,
                ec = ec,
                ed = ed,
                ep = ep,
            )
        }

        // TODO: verificar el orden de los atributos contra el HTML real; aquí se asume
        // que studentSectionId aparece antes que syllabusTeacherId.
        val syllabusButtons = SYLLABUS_BUTTON.findAll(html).toList()
        for (i in grades.indices) {
            val match = syllabusButtons.getOrNull(i) ?: continue
            grades[i] = grades[i].copy(
                studentSectionId = match.groupValues[1],
                syllabusTeacherId = match.groupValues[2],
            )
        }

        return grades
    }

    fun parseMalla(html: String): List<MallaCourse> {
        val courses = mutableListOf<MallaCourse>()
        var cycle = 0
        var insideTable = false

        for (row in TABLE_ROW.findAll(html)) {
            val cells = extractCells(row.groupValues[1])

            if (cells.size >= 2 && cells[0].contains("iclo", ignoreCase = true)) {
                cycle++
                insideTable = true
                continue
            }

            if (insideTable && cells.size >= 6 && cells[0].length > 3 && cells[0].contains(".")) {
                courses += MallaCourse(
                    code = cells[0],
                    name = cells[1],
                    credits = cells[2],
                    hp = cells.getOrElse(3) { "" },
                    hs = cells.getOrElse(4) { "" },
                    ht = cells.getOrElse(5) { "" },
                    hv = cells.getOrElse(6) { "" },
                    prerequisite = cells.getOrElse(7) { "" },
                    cycle = cycle,
                )
            }
        }
        return courses
    }

    fun parsePeriodRecords(html: String, periodName: String, isCurrent: Boolean?): List<AcademicRecord> {
        val records = mutableListOf<AcademicRecord>()
        val decoded = decodeHtmlEntities(html)
        val courseMatches = COURSE_ANCHOR.findAll(html).toList()

        for (i in courseMatches.indices) {
            val raw = courseMatches[i].groupValues[1].cleanHtml()
            val nextIndex = if (i + 1 < courseMatches.size) {
                decoded.indexOf("Curso:", courseMatches[i].range.last + 1).takeIf { it >= 0 } ?: decoded.length
            } else {
                decoded.length
            }
            val section = decoded.substring(courseMatches[i].range.last, nextIndex)

            val credits = fieldIn(section, "Cr.ditos")
            val grade = GRADE_VALUE.find(section)?.groupValues?.get(1)?.cleanHtml() ?: "-"


            // `raw` tiene la forma "19.03420 - ESTADÍSTICA Y PROBABILIDADES"
            val codeMatch = COURSE_CODE.find(raw)
            val code = codeMatch?.groupValues?.get(1) ?: ""
            val name = if (codeMatch != null) {
                raw.substring(codeMatch.range.last).trimStart().trimStart('-', '\u2013').trim()
            } else {
                raw
            }

            records += AcademicRecord(
                courseName = name,
                courseCode = code,
                course = raw,
                credits = credits,
                grade = grade,
                term = periodName,
                isCurrent = isCurrent,
            )
        }
        return records
    }

    fun parseAttendance(html: String): List<AttendanceEntry> {
        val entries = mutableListOf<AttendanceEntry>()

        for (row in TABLE_ROW.findAll(html)) {
            val cells = extractCells(row.groupValues[1])
            if (cells.size < 4) continue

            val course = cells[0]
            if (course.length <= 3) continue
            if (HEADER_WORDS.any { course.contains(it, ignoreCase = true) }) continue

            val total = cells[1].toIntOrNull() ?: continue
            val attended = cells[2].toIntOrNull() ?: 0
            val absent = cells[3].toIntOrNull() ?: 0
            val justified = cells.getOrNull(4)?.toIntOrNull() ?: 0

            entries += AttendanceEntry(
                course = course,
                totalClasses = total,
                dictated = total,
                attended = attended,
                absent = absent,
                justified = justified,
            )
        }
        return entries
    }

    // --- Internos ---

    private fun fieldIn(section: String, label: String): String =
        Regex("$label:\\s*</th>\\s*<td[^>]*>([\\s\\S]*?)</td>")
            .find(section)?.groupValues?.get(1)?.cleanHtml() ?: ""


    private val HEADER_WORDS = listOf("Curso", "Asignatura", "Total", "Nro", "N°")

    private val COURSE_ANCHOR = Regex(
        """Curso:\s*</th>\s*<td[^>]*>\s*<a>\s*([\s\S]*?)\s*</a>""",
    )

    private val GRADES_ROW = Regex("""<tr class="grades">([\s\S]*?)</tr>""")

    private val UNIT_ROW = Regex(
        """<tr class="grades"[^>]*>\s*<td[^>]*>\s*(?:&emsp;)?(?:UNIDAD|1\. PARCIAL|2\. PARCIAL|1[\s\S]*PARCIAL|2[\s\S]*PARCIAL)""",
    )

    private val GRADE_VALUE = Regex(
        """Nota(?: No Oficial| Oficial)?\s*:\s*</th>\s*<th[^>]*>\s*<b>\s*([\d.,]+)\s*</b>""",
    )

    private val SYLLABUS_BUTTON = Regex(
        """data-studentSectionId="([^"]*)"[^>]*data-syllabusTeacherId="([^"]*)"""",
    )

    private val TABLE_ROW = Regex("""<tr[^>]*>([\s\S]*?)</tr>""")

    private val COURSE_CODE = Regex("""(\d+\.\d+)""")

    private val PUBLISHED_SUFFIX = Regex("publicadas[\\s\\S]*")
}
