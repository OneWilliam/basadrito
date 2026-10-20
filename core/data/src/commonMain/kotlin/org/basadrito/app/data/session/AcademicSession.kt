package org.basadrito.app.data.session

import org.basadrito.app.data.di.IntegrationClients
import org.basadrito.app.data.moodle.dto.MoodleSession



class AcademicSession(
    private val createClients: () -> IntegrationClients = { IntegrationClients() }
) {
    internal class Connection(val clients: IntegrationClients, var moodle: MoodleSession? = null)

    private var connection: Connection? = null

    internal fun begin(): Connection {
        clear()
        return Connection(createClients()).also { connection = it }
    }

    internal fun activate(candidate: Connection, moodle: MoodleSession): Boolean {
        if( connection !== candidate) {
            return false
        }

        candidate.moodle = moodle
        return true
    }
    internal fun authenticated(): Connection? = connection?.takeIf { it.moodle != null }

    internal fun isCurrent(candidate: Connection): Boolean {
        return connection == candidate
    }


    internal fun discard(candidate: Connection) {
        if (connection == candidate) {
            clear()
        }
    }
    internal fun clear() {
        val previous = connection
        connection = null
        previous?.moodle = null
        previous?.clients?.close()
    }
}
