package org.basadrito.app.data.intranet

enum class IntranetLoginStep {
    LOGIN_PAGE, CREDENTIALS, AUTHORIZATION, CALLBACK, PROFILE, EVALUATION_REQUIRED,
}

class IntranetLoginFailure(
    val step: IntranetLoginStep,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : IllegalStateException("No se pudo completar el acceso de Intranet en ${step.name}.", cause)

internal class IntranetSessionExpired : IllegalStateException("La sesión de Intranet caducó.")
