package org.basadrito.app.data.http

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import kotlin.coroutines.cancellation.CancellationException

suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

internal fun endpointUrl(baseUrl: String, path: String): String {
    val base = Url(baseUrl)
    require(base.protocol.name in setOf("https", "http") && base.user == null && base.password == null &&
        base.parameters.isEmpty() && base.fragment.isEmpty()) { "URL base no válida." }
    val target = URLBuilder(base).apply {
        encodedPath = encodedPath.trimEnd('/') + "/"
        takeFrom(if (path.startsWith("//")) path else path.removePrefix("/"))
    }.build()
    require(target.protocol == base.protocol && target.host == base.host && target.port == base.port &&
        target.user == null && target.password == null) { "Origen de la solicitud no permitido." }
    return target.toString()
}

internal fun resolveLocation(currentUrl: String, location: String): String {
    val target = io.ktor.http.URLBuilder(io.ktor.http.Url(currentUrl)).apply { takeFrom(location) }.build()
    require(target.protocol == io.ktor.http.URLProtocol.HTTPS && target.port == 443 &&
        target.user == null && target.password == null &&
        target.host in setOf("campus.unjbg.edu.pe", "intranet.unjbg.edu.pe")) {
        "Redirección de autenticación no permitida."
    }
    return target.toString()
}

internal suspend fun HttpClient.getTextFollowingRedirects(url: String, maxHops: Int = 10): String {
    var current = url
    var hops = 0
    while (hops < maxHops) {
        val response: HttpResponse = get(current) { expectSuccess = false }
        val location = response.headers[HttpHeaders.Location]
        if (response.status.value in 300..399) {
            require(!location.isNullOrBlank()) { "Redirección sin destino." }
            current = resolveLocation(current, location)
            hops++
            continue
        }
        check(response.status.value in 200..299) { "Respuesta HTTP no válida (${response.status.value})." }
        return response.bodyAsText()
    }
    error("Demasiadas redirecciones de autenticación.")
}
