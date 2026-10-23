package org.basadrito.app.database

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
fun openAcademicDatabase(): AcademicDatabase {
    val directory = NSFileManager.defaultManager.URLForDirectory(NSCachesDirectory,
        NSUserDomainMask, null, true, null) ?: error("No se pudo abrir el almacenamiento local.")
    return buildAcademicDatabase(Room.databaseBuilder<AcademicDatabase>(name = "${directory.path}/academic-cache.db"))
}
