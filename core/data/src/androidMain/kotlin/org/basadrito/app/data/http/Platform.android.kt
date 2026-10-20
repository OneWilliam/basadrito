package org.basadrito.app.data.http

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual val platformHttpEngine: HttpClientEngineFactory<*> = OkHttp
