package org.basadrito.app.data.intranet.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable




@Serializable
data class ScheduleEventDto(
    val title: String = "",
    val description: String = "",

    val start: String = "",
    val end: String = "",
)


@Serializable
data class AttendanceDto(
    val courseName: String = "",
    val classCount: Int = 0,
    val dictated: Int = 0,
    val assisted: Int = 0,
    val absences: Int = 0,
    val absencesPercentage: Double? = null,
)


@Serializable
data class ProgressDto(
    val courseName: String = "",
    val courseCode: String = "",
    val course: String = "",
    val credits: String = "",

    @SerialName("try") val attempt: String = "",
    val grade: String = "",
    val term: String = "",
    val status: Boolean? = null,
)
