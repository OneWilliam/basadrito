package org.basadrito.app.data.repository

import io.ktor.client.plugins.ResponseException
import org.basadrito.app.data.intranet.IntranetLoginFailure
import org.basadrito.app.data.moodle.MoodleLoginFailure

enum class AuthenticationProvider { MOODLE, INTRANET }

data class AuthenticationDiagnostic(
    val provider: AuthenticationProvider,
    val step: String,
    val errorType: String,
    val httpStatus: Int?,
) {
    fun logLine() = "provider=${provider.name} step=$step type=$errorType http=${httpStatus ?: "none"}"
}

fun interface AuthenticationDiagnostics {
    fun report(diagnostic: AuthenticationDiagnostic)
}

internal fun authenticationDiagnostic(provider: AuthenticationProvider, error: Exception): AuthenticationDiagnostic {
    val chain = generateSequence(error as Throwable) { it.cause }.take(8).toList()
    val intranet = chain.filterIsInstance<IntranetLoginFailure>().firstOrNull()
    val moodle = chain.filterIsInstance<MoodleLoginFailure>().firstOrNull()
    return AuthenticationDiagnostic(
        provider = provider,
        step = intranet?.step?.name ?: moodle?.step?.name ?: "LOGIN",
        errorType = chain.last()::class.simpleName ?: "Exception",
        httpStatus = intranet?.httpStatus ?: chain.filterIsInstance<ResponseException>().firstOrNull()?.response?.status?.value,
    )
}
