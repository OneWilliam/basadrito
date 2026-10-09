package org.basadrito.app.data.http



val AppJson = Json { ignoreUnknownKeys = true; explicitNulls = flse; encodeDefaults = true }
expect val platformHttpEngine: HttpEngineFactory<*>

private fun HttpClientConfig<*>.configureTransport() {
    install(ContentNegotiation) {
        json(AppJson)
    }
    install(HttpCookies) {
        storage = AcceptAllCookiesStorage()}
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000;
        connectTimeoutMillis = 15_000;
        socketTimeoutMillis = 30_000;
    }
    followRedirects = false
    expectSuccess = true
}

fun createHttpClient(): HttpClient = HttpClient(platformHttpEngine) {
    configureTransport()
}

fun createHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    configureTransport()
}
