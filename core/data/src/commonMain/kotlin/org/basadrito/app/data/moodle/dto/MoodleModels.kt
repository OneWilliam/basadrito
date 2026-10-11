package org.basadrito.app.data.moodle.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

object MoodleFunctions {
    const val SERVICE = "moodle_mobile_app"
    const val USER_COURSES = "core_enrol_get_users_courses"
    const val COURSE_CONTENTS = "core_course_get_contents"
    const val ENROLLED_USERS = "core_course_get_enrolled_users"
    const val SITE_INFO = "core_webservice_get_site_info"
}


@Serializable
data class MoodleFunction(
    val name: String = "",
    val version: String = ""
)

@Serializable
data class MoodleSession(
    val token: String = "",
    val userId: Int = 0,
    val fullName: String = "",
    val username: String = "",
)

@Serializable
data class Course(
    val id: Int = 0,
    @SerialName("shortname") val shortName: String = "",
    val fullname: String = "",
    @SerialName("displayname") val displayName: String = "",
    @SerialName("idnumber") val idNumber: String = "",
    @SerialName("enrolledusercount") val enrolledUserCount: Int = 0,
    @SerialName("lastaccess") val lastAccess: Long = 0,
) {
    val displayLabel: String get() = displayName.ifBlank {
        fullname.ifBlank {
            shortName
        }
    }
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
data class SiteInfo(
    val sitename: String = "",
    val username: String = "",
    @SerialName("firstname") val firstName: String = "",
    @SerialName("lastname") val lastName: String = "",
    val fullname: String = "",
    @SerialName("userid") val userId: Int = 0,
    @SerialName("userpictureurl") val userPictureUrl: String? = null,
    @SerialName("lang") val language: String = "",

    val release: String = "",
    val version: String = "",
    val functions: List<MoodleFunction> = emptyList(),
)
