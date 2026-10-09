package org.basadrito.app.domain.auth

data class StudentIdentity(val username: String, val fullName: String)
data class PortalAccess(val student: StudentIdentity, val intranetAvaible: Boolean)

interface AuthRepository {
    suspend fun signIn(username: String, moodlePassword: String, intranetPassword: String): Result<PortalAccess>
    fun signOut()
}
