package org.basadrito.app.data.intranet

import org.basadrito.app.data.http.endpointUrl
import org.basadrito.app.data.http.apiCall
import org.basadrito.app.data.http.AppJson
import org.basadrito.app.data.http.getTextFollowingRedirects
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes

class IntranetClient(private val client: HttpClient,
    private val baseUrl: String = IntranetLoginClient.DEFAULT_INTRANET_URL,
) {
    suspend fun getText(path: String): Result<String> = apiCall {
        client.getTextFollowingRedirects(absolute(path))
    }

    suspend fun getBytes(path: String): Result<ByteArray> = apiCall {
        client.get(absolute(path)).bodyAsBytes()
    }

    fun absolute(path: String): String = endpointUrl(baseUrl, path)

}
