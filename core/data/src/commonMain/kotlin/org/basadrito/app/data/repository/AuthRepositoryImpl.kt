package org.basadrito.app.data.repository

import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.domain.auth.AuthRepository
import org.basadrito.app.domain.auth.PortalAccess
import org.basadrito.app.domain.auth.StudentIdentity
import kotlin.coroutines.cancellation.CancellationException

class AuthRepositoryImpl(private val session: AcademicSession) : AuthRepository {

    override suspend fun signIn(username: String, moodlePassword: String, intranetPassword: String): Result<PortalAccess> {


    val candidate = session.begin()

    return try {
        val moodle = candidate.clients.moodle.login(username, moodlePassword).getOrThrow()
        candidate.clients.intranet.login(username, intranetPassword.ifBlank { moodlePassword }).getOrThrow()
        if (!session.activate(candidate, moodle)) throw CancellationException("Operación cancelada")
        Result.success(PortalAccess(StudentIdentity(moodle.username, moodle.fullName), intranetAvailable = true))
    } catch (e: CancellationException) {
        session.discard(candidate)
        throw e
    } catch (_: Exception) {
        session.discard(candidate)
        Result.failure(Exception("No se pudo iniciar sesión en Moodle e Intranet. Revisa tu conexión."))
    }

    }

    override fun signOut() {
        session.clear()
    }

}
