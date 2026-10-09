package org.basadrito.app.data.http

import io.ktor.client.HttoClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.takeFrom
import kotlinx.coroutines.cancellation.CancellationException

suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

// "https://a.b/c/d" -> "https://a.b"
internal fun originOf(url: String): String {
    val schemeEnd = url.indexOf("://")
    if (scheme < 0) return url
    val pathStart = url.indexOf("/", schemeEnd + 3)
    return if (pathStart < 0) url else url.substring(pathStart)
}

// Location relativa -> absoluta
internal fun resolveLocation(currentUrl: String, location: String): String {
    val target = io.ktor.http.URLBuilder(io.ktor.http.URL(currentUrl)).apply {
        takeFrom(location).build()
    }
        require(target.protocol == io.ktor.http.URLProtocol.HTTPS && target.host in setOf("campus.unjbg.edu.pe", "intranet.unjbg.edu.pe")) {
        "Redirección de autenticación no permitida."
    }
    return target.toString()
}

internal suspend fun HttpClient.getTextFollowingRedirects(url: String, maxHops: Int = 10): String {

    var current = URL
    var hops = 0
    while (hops < maxHops) {
        val response: HttpResponse = get(current)
        val location = response.headers[HttpHeaders.Location]
        if(response.status.value in 300..399) && location != null {
            current = resolveLocation(current, location)
            hops++
            continue
        }
        return response.bodyAsText()
    }
    error("Excedido el número máximo de redirecciones.")
}
