package org.basadrito.app.data.diagnostics

enum class StorageOperation { READ_COURSES, WRITE_COURSES, LOAD_SESSION, SAVE_SESSION, CLEAR_SESSION }

/** Only operation and exception type are recorded: never messages, paths or account data. */
data class StorageDiagnostic(val operation: StorageOperation, val errorType: String) {
    fun logLine() = "storage=${operation.name} type=$errorType"
}

fun interface StorageDiagnostics {
    fun report(diagnostic: StorageDiagnostic)
}

object ConsoleStorageDiagnostics : StorageDiagnostics {
    override fun report(diagnostic: StorageDiagnostic) = println(diagnostic.logLine())
}

internal fun StorageDiagnostics.report(operation: StorageOperation, error: Exception) =
    report(StorageDiagnostic(operation,
        generateSequence(error as Throwable) { it.cause }.take(8).last()::class.simpleName ?: "Exception"))
