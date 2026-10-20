package org.basadrito.app.domain.auth

data class StudentIdentity(val username: String, val fullName: String)
enum class IntranetRequirement { TEACHER_EVALUATION }

data class PortalAccess(
    val student: StudentIdentity,
    val intranetAvailable: Boolean,
    val intranetRequirement: IntranetRequirement? = null,
)

interface AuthRepository {
    suspend fun signIn(username: String, moodlePassword: String, intranetPassword: String): Result<PortalAccess>
    fun signOut()
}
