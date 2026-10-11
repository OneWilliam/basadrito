package org.basadrito.tools

import kotlinx.coroutines.runBlocking
import org.basadrito.app.data.di.IntegrationClients
import org.basadrito.app.data.intranet.IntranetLoginFailure
import io.github.cdimascio.dotenv.DotenvException
import io.github.cdimascio.dotenv.dotenv

fun main(args: Array<String>): Unit = runBlocking {
    if (args.size == 1 && args[0] == "--help") {
        println("None")
        return@runBlocking
    }
    val credentials = try {
        loadCredentials(args)
    } catch (_: DotenvException) {
        println("No se pudo cargar .env.")
        return@runBlocking
    } catch (error: IllegalArgumentException) {
        println(error.message)
        return@runBlocking
    }
    if (credentials == null) {
        println("Faltan credenciales.")
        return@runBlocking
    }
    val username = credentials[0].trim()
    val moodlePasswordChars = credentials[1].toCharArray()
    val intranetPasswordChars = credentials[2].toCharArray()

    credentials[1] = ""
    credentials[2] = ""
    try {
        val clients = IntegrationClients()
        try {
            val moodle = try {
                clients.moodle.login(username, moodlePasswordChars.concatToString()).getOrNull()
            } finally { moodlePasswordChars.fill('\u0000') }
            println(if (moodle == null) "Acceso Moodle: fallo." else "Acceso Moodle: correcto.")

            val intranetResult = try {
                clients.intranet.login(username, intranetPasswordChars.concatToString())
            } finally { intranetPasswordChars.fill('\u0000') }
            val intranet = intranetResult.isSuccess
            println(if (intranet) "Acceso Intranet: correcto." else "Acceso Intranet: fallo.")
            val failure = intranetResult.exceptionOrNull()
            if (failure is IntranetLoginFailure) {
                println("Etapa Intranet: ${failure.step.name}")
                failure.httpStatus?.let { println("Estado HTTP: $it") }
            } else if (failure != null) {
                println("Tipo de fallo Intranet: ${failure::class.simpleName}")
            }

            if (moodle == null || !intranet) {
                println("Comprobación: fallo.")
                return@runBlocking
            }
            println("Comprobación conjunta: correcta.")
            clients.intranet.getProfile().fold(
                onSuccess = { profile ->
                    println(if (profile.fullName.isBlank()) "Perfil Intranet: nombre no detectado."
                        else "Perfil Intranet: nombre detectado (${profile.fullName.length} caracteres).")
                },
                onFailure = { println("Consulta de perfil Intranet: fallo (${it::class.simpleName}).") },
            )
            val courses = clients.moodle.getCourses(moodle.token, moodle.userId).getOrNull()
            println(if (courses == null) "Consulta de cursos: fallo." else "Consulta de cursos: ${courses.size} resultados.")
        } finally { clients.close() }
    } finally {
        moodlePasswordChars.fill('\u0000')
        intranetPasswordChars.fill('\u0000')
    }
}

private fun loadCredentials(args: Array<String>): Array<String>? {
    if (args.isNotEmpty()) {
        require(args.size == 3) { "Se requieren exactamente tres argumentos, o ninguno para usar .env." }
        return args.takeIf { it.all(String::isNotBlank) }
    }
    val values = dotenv {
        directory = "."
        ignoreIfMissing = true
    }
    val keys = listOf("BASADRITO_USERNAME", "BASADRITO_MOODLE_PASSWORD", "BASADRITO_INTRANET_PASSWORD")
    val credentials = keys.map { values[it].orEmpty() }.toTypedArray()
    return credentials.takeIf { it.all(String::isNotBlank) }
}
