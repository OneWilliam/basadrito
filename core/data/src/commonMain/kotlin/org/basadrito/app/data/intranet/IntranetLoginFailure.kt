package org.basadrito.app.data.intranet

enum class IntranetLoginStep {
    LOGIN_PAGE, CREDENTIALS, AUTHORIZATION, CALLBACK, PROFILE,
}

class IntranetLoginFailure(
    val step: IntranetLoginStep,
    val httpStatus: Int? = null,
) : IllegalStateException("No se pudo completar el acceso de Intranet en ${step.name}.")
