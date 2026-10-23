package org.basadrito.app.data

import io.ktor.client.engine.mock.*
import kotlinx.coroutines.test.runTest
import org.basadrito.app.data.http.createHttpClient
import org.basadrito.app.data.moodle.MoodleClient
import kotlin.test.*

class MoodleDownloadTest {
    @Test fun fileAdapterNormalizesMoodlePathAndKeepsAuthenticationInDataLayer() = runTest {
        val client = createHttpClient(MockEngine { request ->
            assertEquals("aula.unjbg.edu.pe", request.url.host)
            assertEquals("/webservice/pluginfile.php/12/file.pdf", request.url.encodedPath)
            assertEquals("fixture-token", request.url.parameters["token"])
            assertNull(request.url.parameters["wstoken"])
            respond("fixture-file")
        })
        try {
            val api = MoodleClient(client)
            assertEquals("fixture-file", api.downloadFile("fixture-token", "https://aula.unjbg.edu.pe/pluginfile.php/12/file.pdf?wstoken=obsolete").getOrThrow().decodeToString())
        } finally { client.close() }
    }
    @Test fun foreignOriginAndNonFilePathsAreRejectedBeforeSendingAnyRequest() = runTest {
        var requests = 0
        val client = createHttpClient(MockEngine { requests++; respond("unexpected") })
        try {
            val api = MoodleClient(client)
            for (url in listOf("https://example.test/webservice/pluginfile.php/12/f", "https://aula.unjbg.edu.pe/login/index.php",
                "http://aula.unjbg.edu.pe/webservice/pluginfile.php/12/f", "https://aula.unjbg.edu.pe@example.test/pluginfile.php/12/f")) {
                assertTrue(api.downloadFile("fixture-token", url).isFailure)
            }
            assertEquals(0, requests)
        } finally { client.close() }
    }
}
