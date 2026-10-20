package org.basadrito.app.data.repository

import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.domain.courses.CourseSummary
import org.basadrito.app.domain.courses.CoursesRepository
import kotlin.coroutines.cancellation.CancellationException

class CoursesRepositoryImpl(private val session: AcademicSession) : CoursesRepository {
    override suspend fun getCourses(): Result<List<CourseSummary>> {
        val connection = session.authenticated()
            ?: return Result.failure(IllegalStateException("Inicia sesión para consultar cursos."))
        val account = connection.moodle
            ?: return Result.failure(IllegalStateException("La sesión terminó."))
        val result = connection.clients.moodle.getCourses(account.token, account.userId)
        if (!session.isCurrent(connection)) throw CancellationException("La sesión terminó")
        return result.fold(
            onSuccess = { courses -> Result.success(courses.map { CourseSummary(it.id, it.displayLabel, it.shortName) }) },
            onFailure = { Result.failure(IllegalStateException("No se pudieron cargar los cursos. Intenta nuevamente.")) },
        )
    }
}
