package org.basadrito.tools

import kotlinx.coroutines.runBlocking
import org.basadrito.app.data.di.IntegrationClients

fun main(args: Array<String>): Unit = runBlocking {
    if (args.size != 2 || args[1].isBlank() || args[0].isBlank()) {
        println("Se requieren dos argumentos: <código> <contraseña>.")
        return@runBlocking
    }
    val username = args[0].trim()
    val passwordChars = args[1].toCharArray()
    args[1] = ""
    try {
        if (passwordChars.isEmpty() || passwordChars.all { it.isWhitespace() }) {
            println("Se requieren código y contraseña. No se ha iniciado sesión.")
            return@runBlocking
        }
        val clients = IntegrationClients()
        try {
            val session = clients.moodle.login(username, String(passwordChars)).getOrNull()
            if (session == null) { println("Acceso Moodle: fallo. No se registran detalles sensibles."); return@runBlocking }
            println("Acceso Moodle: correcto.")
            val result = clients.moodle.getCourses(session.token, session.userId)
            result.fold(
                onSuccess = { println("Cursos: ${it.size}") },
                onFailure = { println("Fallo de cursos: ${it::class.simpleName}") },
            )
        } finally { clients.close() }
    } finally { passwordChars.fill('\u0000') }
}
