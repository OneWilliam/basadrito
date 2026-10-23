package org.basadrito.app.data.diagnostics

enum class StorageAction { READ, WRITE, CLEAR }
enum class StorageArea { COURSE_CACHE, ACADEMIC_CACHE, SESSION_STORE }

/** Only operation and exception type are recorded: never messages, paths or account data. */
data class StorageDiagnostic(val area: StorageArea, val action: StorageAction, val errorType: String) {
    fun logLine() = "storage=${area.name} action=${action.name} type=$errorType"
}

fun interface StorageDiagnostics {
    fun report(diagnostic: StorageDiagnostic)
}

object ConsoleStorageDiagnostics : StorageDiagnostics {
    override fun report(diagnostic: StorageDiagnostic) = println(diagnostic.logLine())
}

internal fun StorageDiagnostics.report(area: StorageArea, action: StorageAction, error: Exception) =
    report(StorageDiagnostic(area, action,
        generateSequence(error as Throwable) { it.cause }.take(8).last()::class.simpleName ?: "Exception"))
