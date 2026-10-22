package org.basadrito.app.data.repository

import io.ktor.client.plugins.ResponseException
import org.basadrito.app.data.intranet.IntranetSessionExpired
import org.basadrito.app.data.moodle.MoodleSessionExpired
import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.domain.auth.AuthRepository
import org.basadrito.app.domain.auth.PortalAccess
import org.basadrito.app.domain.auth.StudentIdentity
import org.basadrito.app.domain.auth.IntranetRequirement
import org.basadrito.app.domain.auth.SessionRecoveryException
import org.basadrito.app.domain.auth.SessionRecoveryFailure
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
            val remembered = session.persist(candidate)
            session.requireCurrent(candidate)
            Result.success(PortalAccess(
                StudentIdentity(moodle.username, moodle.fullName),
                intranetAvailable = intranetReady,
                intranetRequirement = if (intranetReady) null else IntranetRequirement.TEACHER_EVALUATION,
                sessionRemembered = remembered,
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

    override suspend fun restoreSession(): Result<PortalAccess?> {
        val candidate = session.beginRestoration()
        val saved = try {
            session.saved(candidate)
        } catch (cancelled: CancellationException) {
            session.discard(candidate)
            throw cancelled
        } catch (_: Exception) {
            session.discard(candidate)
            return Result.failure(SessionRecoveryException(SessionRecoveryFailure.STORAGE))
        }
        if (saved == null) {
            session.discard(candidate)
            return Result.success(null)
        }
        var provider = AuthenticationProvider.MOODLE
        return try {
            if (saved.version != 1 || saved.moodle.token.isBlank() || saved.moodle.userId <= 0) throw MoodleSessionExpired()
            candidate.cookies.restore(saved.cookies)
            val site = candidate.clients.moodle.getSiteInfo(saved.moodle.token).getOrThrow()
            check(site.userId > 0) { "Moodle no devolvió una identidad reconocible." }
            // A token that now identifies somebody else must never restore the saved account.
            if (site.userId != saved.moodle.userId) throw MoodleSessionExpired()
            val moodle = saved.moodle.copy(
                username = site.username.ifBlank { saved.moodle.username },
                fullName = site.fullname.ifBlank { "${site.firstName} ${site.lastName}".trim() }.ifBlank { saved.moodle.fullName },
            )
            provider = AuthenticationProvider.INTRANET
            val intranetReady = try {
                candidate.clients.intranet.checkAccess().getOrThrow()
                true
            } catch (required: IntranetLoginFailure) {
                if (required.step != IntranetLoginStep.EVALUATION_REQUIRED) throw required
                false
            }
            if (!session.activate(candidate, moodle)) throw CancellationException("Operación cancelada")
            val remembered = session.persist(candidate)
            session.requireCurrent(candidate)
            Result.success(PortalAccess(
                StudentIdentity(moodle.username, moodle.fullName),
                intranetAvailable = intranetReady,
                intranetRequirement = if (intranetReady) null else IntranetRequirement.TEACHER_EVALUATION,
                sessionRemembered = remembered,
            ))
        } catch (cancelled: CancellationException) {
            session.discard(candidate)
            throw cancelled
        } catch (error: Exception) {
            session.requireCurrent(candidate)
            val causes = generateSequence(error as Throwable) { it.cause }.take(8).toList()
            val expired = causes.any { it is MoodleSessionExpired || it is IntranetSessionExpired ||
                it is ResponseException && it.response.status.value in setOf(401, 403) }
            try { diagnostics.report(authenticationDiagnostic(provider, error)) } catch (_: Exception) { }
            try {
                if (expired) session.forgetSaved(candidate)
            } catch (_: Exception) {
                return Result.failure(SessionRecoveryException(SessionRecoveryFailure.STORAGE))
            } finally { session.discard(candidate) }
            Result.failure(SessionRecoveryException(if (expired) SessionRecoveryFailure.EXPIRED else SessionRecoveryFailure.TEMPORARY))
        }
    }

    override fun signOut() = session.clear()
}
