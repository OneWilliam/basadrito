package org.basadrito.app.data.intranet.parser

import org.basadrito.app.data.intranet.dto.CourseGradeDetail
import org.basadrito.app.data.intranet.dto.UnitGradeDetail


object GradeDetailParser {

    fun parse(html: String): List<CourseGradeDetail> {
        val courses = mutableListOf<CourseGradeDetail>()

        for (match in COURSE_BLOCK.findAll(html)) {
            val courseName = match.groupValues[1].cleanHtml()
            val studentSectionId = match.groupValues[2]
            val syllabusTeacherId = match.groupValues[3]

            val startIndex = match.range.last + 1
            val nextCourse = html.indexOf("Curso:", startIndex).takeIf { it >= 0 } ?: html.length
            val section = html.substring(startIndex, nextCourse)

            val units = parseUnits(section)

            courses += CourseGradeDetail(
                courseName = courseName,
                section = fieldIn(section, "Secci.n"),
                credits = fieldIn(section, "Cr.ditos"),
                formula = fieldIn(section, "F.rmula"),
                units = units,
                reportedFinalGrade = fieldIn(section, "Nota(?: No Oficial| Oficial)?").takeIf { it.isNotBlank() },
                studentSectionId = studentSectionId,
                syllabusTeacherId = syllabusTeacherId,
            )
        }
        return courses
    }

    private fun parseUnits(section: String): List<UnitGradeDetail> {
        val units = mutableListOf<UnitGradeDetail>()

        for (block in section.split(UNIT_SPLIT)) {
            if (!block.contains("UNIDAD") && !block.contains("PARCIAL")) continue

            val rawName = UNIT_NAME.find(block)?.value?.trim() ?: continue
            val unitName = decodeHtmlEntities(rawName)

            val unitTotal = TOTAL_BOLD.find(block)?.groupValues?.get(1)
                ?: TOTAL_CENTER.find(block)?.groupValues?.get(1)
                ?: "-"

            val ec = componentGrade(block, "EC")
            val ed = componentGrade(block, "ED")
            val ep = componentGrade(block, "EP")

            units += UnitGradeDetail(
                unitName = unitName,
                ec = ec?.grade ?: "-",
                ecPercent = ec?.percent ?: 0,
                ed = ed?.grade ?: "-",
                edPercent = ed?.percent ?: 0,
                ep = ep?.grade ?: "-",
                epPercent = ep?.percent ?: 0,
                unitTotal = unitTotal,
            )
        }
        return units
    }

    private fun componentGrade(block: String, code: String): ComponentGrade? {
        val regex = Regex(
            "&emsp;&emsp;$code</td>\\s*<td[^>]*data-value=\"(\\d+)\">\\s*(\\d+)\\s*%</td>[\\s\\S]*?<td[^>]*>([\\d.\\-]+)",
        )
        val match = regex.find(block) ?: return null
        return ComponentGrade(
            percent = match.groupValues[2].toIntOrNull() ?: 0,
            grade = match.groupValues[3],
        )
    }

    private data class ComponentGrade(val percent: Int, val grade: String)

    private fun fieldIn(section: String, label: String): String =
        Regex("$label:\\s*</th>\\s*<td[^>]*>([\\s\\S]*?)</td>")
            .find(section)?.groupValues?.get(1)?.cleanHtml() ?: ""


    private val COURSE_BLOCK = Regex(
        "Curso:\\s*</th>\\s*<td[^>]*>\\s*<a>\\s*([\\s\\S]*?)\\s*</a>[\\s\\S]*?" +
            "data-studentSectionId=\"([^\"]*)\"[\\s\\S]*?" +
            "data-syllabusTeacherId=\"([^\"]*)\"",
    )

    private val UNIT_SPLIT = Regex(
        "(?=<tr class=\"grades\"[^>]*>\\s*<td[^>]*>\\s*(?:&emsp;)?(?:UNIDAD|\\d+[\u00B0&#x]*[0-9Bb]*;?\\s*PARCIAL))",
    )

    private val UNIT_NAME = Regex("UNIDAD\\s*\\d+|\\d+[\u00B0&#x]*[0-9Bb]*;?\\s*PARCIAL")

    private val TOTAL_BOLD = Regex("""font-weight:bold;">\s*([\d.]+)\s*</td>""")
    private val TOTAL_CENTER = Regex("""text-align:center;">\s*([\d.]+)\s*</td>""")
}
