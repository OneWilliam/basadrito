package org.basadrito.app.data.repository

import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.domain.auth.AuthRepository
import org.basadrito.app.domain.auth.PortalAccess
import org.basadrito.app.domain.auth.StudentIdentity
import org.basadrito.app.domain.auth.IntranetRequirement
import org.basadrito.app.data.intranet.IntranetLoginFailure
import org.basadrito.app.data.intranet.IntranetLoginStep
import kotlin.coroutines.cancellation.CancellationException

class AuthRepositoryImpl(
    private val session: AcademicSession,
    private val diagnostics: AuthenticationDiagnostics = AuthenticationDiagnostics {},
) : AuthRepository {
    override suspend fun signIn(username: String, moodlePassword: String, intranetPassword: String): Result<PortalAccess> {
        val candidate = session.begin()
        var provider = AuthenticationProvider.MOODLE
        return try {
            val moodle = candidate.clients.moodle.login(username, moodlePassword).getOrThrow()
            provider = AuthenticationProvider.INTRANET
            val intranetReady = try {
                candidate.clients.intranet.login(username, intranetPassword.ifBlank { moodlePassword }).getOrThrow()
                true
            } catch (required: IntranetLoginFailure) {
                if (required.step != IntranetLoginStep.EVALUATION_REQUIRED) throw required
                false
            }
            if (!session.activate(candidate, moodle)) throw CancellationException("Operación cancelada")
            Result.success(PortalAccess(
                StudentIdentity(moodle.username, moodle.fullName),
                intranetAvailable = intranetReady,
                intranetRequirement = if (intranetReady) null else IntranetRequirement.TEACHER_EVALUATION,
            ))
        } catch (cancelled: CancellationException) {
            session.discard(candidate)
            throw cancelled
        } catch (error: Exception) {
            session.discard(candidate)

            try { diagnostics.report(authenticationDiagnostic(provider, error)) } catch (_: Exception) { }
            val service = if (provider == AuthenticationProvider.MOODLE) "Moodle" else "Intranet"
            Result.failure(IllegalStateException("No se pudo iniciar sesión en $service. Revisa tus datos y conexión."))
        }
    }

    override fun signOut() = session.clear()
}
