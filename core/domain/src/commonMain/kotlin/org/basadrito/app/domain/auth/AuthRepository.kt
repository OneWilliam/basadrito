package org.basadrito.app.domain.auth

data class StudentIdentity(val username: String, val fullName: String)
enum class IntranetRequirement { TEACHER_EVALUATION }

data class PortalAccess(
    val student: StudentIdentity,
    val intranetAvailable: Boolean,
    val intranetRequirement: IntranetRequirement? = null,
    val sessionRemembered: Boolean = false,
)

enum class SessionRecoveryFailure { EXPIRED, TEMPORARY, STORAGE }

class SessionRecoveryException(val reason: SessionRecoveryFailure) : IllegalStateException(when (reason) {
    SessionRecoveryFailure.EXPIRED -> "Tu acceso guardado ya no es válido. Inicia sesión nuevamente."
    SessionRecoveryFailure.TEMPORARY -> "No se pudo comprobar el acceso guardado. Revisa tu conexión e intenta nuevamente."
    SessionRecoveryFailure.STORAGE -> "No se pudo abrir el acceso guardado. Puedes reintentar o iniciar sesión nuevamente."
}) {
    val canRetry: Boolean get() = reason != SessionRecoveryFailure.EXPIRED
}

interface AuthRepository {
    suspend fun restoreSession(): Result<PortalAccess?>
    suspend fun signIn(username: String, moodlePassword: String, intranetPassword: String): Result<PortalAccess>
    fun signOut()
}
