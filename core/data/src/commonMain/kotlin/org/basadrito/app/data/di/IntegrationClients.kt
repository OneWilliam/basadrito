package org.basadrito.app.data.di

import io.ktor.client.HttpClient
import org.basadrito.app.data.http.createHttpClient
import org.basadrito.app.data.moodle.MoodleClient
import org.basadrito.app.data.moodle.MoodleDataSource
import org.basadrito.app.data.intranet.IntranetClient
import org.basadrito.app.data.intranet.IntranetDataSource
import org.basadrito.app.data.intranet.IntranetLoginClient


class IntegrationClients(val httpClient: HttpClient = createHttpClient()) {
    val moodle = MoodleDataSource(MoodleClient(httpClient))
    val intranet = IntranetDataSource(IntranetLoginClient(httpClient), IntranetClient(httpClient))
    fun close() = httpClient.close()
}
