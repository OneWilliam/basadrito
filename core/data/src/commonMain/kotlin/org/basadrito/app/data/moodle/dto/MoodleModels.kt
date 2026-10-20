package org.basadrito.app.data.moodle.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement




object MoodleFunctions {
    const val SERVICE = "moodle_mobile_app"
    const val SITE_INFO = "core_webservice_get_site_info"
    const val USER_COURSES = "core_enrol_get_users_courses"
    const val COURSE_GRADES = "gradereport_overview_get_course_grades"
    const val ASSIGNMENTS = "mod_assign_get_assignments"
    const val SUBMISSION_STATUS = "mod_assign_get_submission_status"
    const val COURSE_CONTENTS = "core_course_get_contents"
    const val ENROLLED_USERS = "core_enrol_get_enrolled_users"
}


@Serializable
data class MoodleTokenResponse(
    val token: String? = null,
    @SerialName("privatetoken") val privateToken: String? = null,
    val error: String? = null,
    @SerialName("errorcode") val errorCode: String? = null,
) {
    val isSuccess: Boolean get() = !token.isNullOrBlank()
}


@Serializable
data class MoodleWarning(
    val item: String = "",
    @SerialName("itemid") val itemId: Long = 0,
    @SerialName("warningcode") val warningCode: String = "",
    val message: String = "",
)


@Serializable
data class MoodleSession(
    val token: String = "",
    val userId: Int = 0,
    val fullName: String = "",
    val username: String = "",
)


@Serializable
data class SiteInfo(
    val sitename: String = "",
    val username: String = "",
    @SerialName("firstname") val firstName: String = "",
    @SerialName("lastname") val lastName: String = "",
    val fullname: String = "",
    @SerialName("userid") val userId: Int = 0,

    @SerialName("siteurl") val siteUrl: String = "",
    @SerialName("userpictureurl") val userPictureUrl: String? = null,
    @SerialName("lang") val language: String = "",

    val release: String = "",

    val version: String = "",

    val functions: List<MoodleFunction> = emptyList(),
)

@Serializable
data class MoodleFunction(
    val name: String = "",
    val version: String = "",
)


@Serializable
data class Course(
    val id: Int = 0,
    @SerialName("shortname") val shortName: String = "",
    val fullname: String = "",
    @SerialName("displayname") val displayName: String = "",
    @SerialName("enrolledusercount") val enrolledUserCount: Int = 0,
    @SerialName("idnumber") val idNumber: String = "",
    @SerialName("visible") val visible: Int = 1,
    @SerialName("startdate") val startDate: Long = 0,
    @SerialName("enddate") val endDate: Long = 0,
    @SerialName("lastaccess") val lastAccess: Long = 0,
    val progress: Double? = null,
    @SerialName("category") val categoryId: Int = 0,
) {

    val displayLabel: String get() = displayName.ifBlank { fullname.ifBlank { shortName } }
}


@Serializable
data class GradeResponse(
    val grades: List<Grade> = emptyList(),
    val warnings: List<JsonElement> = emptyList(),
)

@Serializable
data class Grade(
    @SerialName("courseid") val courseId: Int = 0,
    val grade: String = "",
    @SerialName("rawgrade") val rawGrade: String? = null,
) {

    val gradeOrNull: Double?
        get() = rawGrade?.toDoubleOrNull() ?: grade.toDoubleOrNull()
}


@Serializable
data class AssignmentResponse(
    val courses: List<CourseAssignments> = emptyList(),
    val warnings: List<JsonElement> = emptyList(),
)

@Serializable
data class CourseAssignments(
    val id: Int = 0,
    val fullname: String = "",
    val shortname: String = "",
    val assignments: List<Assignment> = emptyList(),
)

@Serializable
data class Assignment(
    val id: Int = 0,
    val cmid: Int = 0,
    val course: Int = 0,
    val name: String = "",
    @SerialName("duedate") val dueDate: Long = 0,
    @SerialName("allowsubmissionsfromdate") val allowSubmissionsFromDate: Long = 0,
    @SerialName("cutoffdate") val cutOffDate: Long = 0,
    val grade: Int = 0,
    val intro: String = "",
    @SerialName("introattachments") val introAttachments: List<MoodleFile> = emptyList(),
) {
    val hasDueDate: Boolean get() = dueDate > 0
}


@Serializable
data class MoodleFile(
    val filename: String = "",
    val filepath: String = "",
    @SerialName("filesize") val fileSize: Long = 0,
    @SerialName("fileurl") val fileUrl: String = "",
    @SerialName("timemodified") val timeModified: Long = 0,
    @SerialName("mimetype") val mimeType: String = "",
)


@Serializable
data class SubmissionStatus(
    @SerialName("lastattempt") val lastAttempt: LastAttempt? = null,
    val warnings: List<JsonElement> = emptyList(),
)

@Serializable
data class LastAttempt(
    val submission: Submission? = null,
    val graded: Boolean = false,
    @SerialName("gradingstatus") val gradingStatus: String = "",
    val feedback: Feedback? = null,
)

@Serializable
data class Feedback(
    @SerialName("grade") val grade: GradeValue? = null,
    @SerialName("gradefordisplay") val gradeForDisplay: String = "",
)

@Serializable
data class GradeValue(
    val rawgrade: Double? = null,
)

@Serializable
data class Submission(
    val id: Int = 0,
    @SerialName("userid") val userId: Int = 0,
    val status: String = "",
    @SerialName("timecreated") val timeCreated: Long = 0,
    @SerialName("timemodified") val timeModified: Long = 0,
)


@Serializable
data class EnrolledUser(
    val id: Int = 0,
    val username: String = "",
    val fullname: String = "",
    @SerialName("firstname") val firstName: String = "",
    @SerialName("lastname") val lastName: String = "",
    val roles: List<EnrolledRole> = emptyList(),
)

@Serializable
data class EnrolledRole(
    @SerialName("roleid") val roleId: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("shortname") val shortName: String = "",
    @SerialName("idnumber") val idNumber: String = "",
) {
    val isTeacher: Boolean get() = shortName in TEACHER_ROLES

    companion object {
        val TEACHER_ROLES = setOf("editingteacher", "teacher", "coursecreator")
    }
}


@Serializable
data class CourseSection(
    val id: Int = 0,
    val name: String = "",
    val summary: String = "",
    val modules: List<CourseModule> = emptyList(),
)


@Serializable
data class CourseModule(
    val id: Int = 0,
    val name: String = "",
    val modname: String = "",
    val modplural: String = "",
    val description: String = "",
    @SerialName("url") val url: String? = null,
    val contents: List<ModuleContent> = emptyList(),
)

@Serializable
data class ModuleContent(
    val type: String = "",
    val filename: String = "",
    val filepath: String = "",
    @SerialName("filesize") val fileSize: Long = 0,
    @SerialName("fileurl") val fileUrl: String = "",
    val mimetype: String = "",
)
