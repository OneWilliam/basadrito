package org.basadrito.app.data.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json


val AppJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
expect val platformHttpEngine: HttpClientEngineFactory<*>

private fun HttpClientConfig<*>.configureTransport(cookieStorage: CookiesStorage) {
    install(ContentNegotiation) {
        json(AppJson)
    }
    install(HttpCookies) {
        storage = cookieStorage
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000;
        connectTimeoutMillis = 15_000;
        socketTimeoutMillis = 30_000;
    }
    followRedirects = false
    expectSuccess = true
}

fun createHttpClient(cookieStorage: CookiesStorage = AcceptAllCookiesStorage()): HttpClient = HttpClient(platformHttpEngine) {
    configureTransport(cookieStorage)
}

fun createHttpClient(engine: HttpClientEngine, cookieStorage: CookiesStorage = AcceptAllCookiesStorage()): HttpClient = HttpClient(engine) {
    configureTransport(cookieStorage)
}
