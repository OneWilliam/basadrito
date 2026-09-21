package org.basadrito.app.core.model.moodle

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


object MoodleFunctions {
    const val SERVICE = "moodle_mobile_app"
    const val SITE_INFO = "core_webservice_get_size_info"
    const val USER_COURSES = "core_enrol_get_users_courses"
    const val COURSE_GRADES = "graderport_overview_get_course_grades"
    const val ASSIGNMENTS = "mod_assign_get_assignments"
    const val SUBMISSION_STATUS = "mod_assign_get_submission_status"
    const val COURSE_CONTENTS = "core_course_get_contents"
    const val ENROLLED_USERS = "core_enrol_get_enrolled_users"
}


@Serializable
data class MoodleSession(
    val token: String = "",
    val userId: Int = 0,
    val fullName: String = "",
    val username: String = ""
)
