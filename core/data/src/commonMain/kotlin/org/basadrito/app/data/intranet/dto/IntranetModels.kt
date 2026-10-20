package org.basadrito.app.data.intranet.dto

import kotlinx.serialization.Serializable
import org.basadrito.app.data.intranet.format.toDoubleFlexible



/** Perfil del estudiante, extraído de `/perfil` (HTML). */
@Serializable
data class StudentProfile(
    val fullName: String = "",
    val code: String = "",
    val career: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
)

/** Opción del selector de periodos académicos. */
@Serializable
data class PeriodOption(
    val id: String = "",
    val name: String = "",
)

/** Una clase del horario semanal. */
@Serializable
data class ScheduleEntry(
    val course: String = "",
    /** Descripción del evento: sección, aula o docente según lo que devuelva el servidor. */
    val detail: String = "",
    val day: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val startIso: String = "",
    val endIso: String = "",
)

/** Notas de un curso en un periodo, con sus componentes EC/ED/EP. */
@Serializable
data class IntranetGrade(
    val course: String = "",
    val section: String = "",
    val credits: String = "",
    val period: String = "",
    val unitGrade: String = "",
    val ec: String = "",
    val ed: String = "",
    val ep: String = "",
    val studentSectionId: String = "",
    val syllabusTeacherId: String = "",
)

/** Curso de la malla curricular. */
@Serializable
data class MallaCourse(
    val code: String = "",
    val name: String = "",
    val credits: String = "",
    val hp: String = "",
    val hs: String = "",
    val ht: String = "",
    val hv: String = "",
    val prerequisite: String = "",
    val cycle: Int = 0,
) {
    val creditsValue: Double get() = credits.toDoubleFlexible() ?: 0.0
}

/** Un curso dentro de un ciclo o periodo del historial académico. */
@Serializable
data class AcademicRecord(
    val courseName: String = "",
    val courseCode: String = "",
    /** Nombre completo tal como lo devuelve el servidor ("19.03420 - ESTADÍSTICA..."). */
    val course: String = "",
    val credits: String = "",
    val attempt: String = "",
    val grade: String = "",
    val term: String = "",
    val approved: Boolean? = null,
    val cycle: Int = 0,
    val isCurrent: Boolean? = null,
) {
    val gradeValue: Double? get() = grade.toDoubleFlexible()
}

/** Asistencia acumulada de un curso. */
@Serializable
data class AttendanceEntry(
    val course: String = "",
    val totalClasses: Int = 0,
    val dictated: Int = 0,
    val attended: Int = 0,
    val absent: Int = 0,
    val justified: Int = 0,
    /** Porcentaje de inasistencia reportado por la intranet. */
    val absencePercentage: Double? = null,
)

/** Nota de una unidad o parcial, con el peso de cada componente. */
@Serializable
data class UnitGradeDetail(
    val unitName: String = "",
    val ec: String = "-",
    val ecPercent: Int = 0,
    val ed: String = "-",
    val edPercent: Int = 0,
    val ep: String = "-",
    val epPercent: Int = 0,
    val unitTotal: String = "-",
) {
    val unitTotalValue: Double? get() = unitTotal.toDoubleFlexible()
}

/** Detalle completo de notas de un curso. */
@Serializable
data class CourseGradeDetail(
    val courseName: String = "",
    val section: String = "",
    val credits: String = "",
    val formula: String = "",
    val units: List<UnitGradeDetail> = emptyList(),
    val reportedFinalGrade: String? = null,
    val studentSectionId: String = "",
    val syllabusTeacherId: String = "",
) {
    val finalGradeValue: Double? get() = reportedFinalGrade?.toDoubleFlexible()
}
