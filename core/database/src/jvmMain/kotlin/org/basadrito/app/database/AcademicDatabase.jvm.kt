package org.basadrito.app.database

import androidx.room.Room
import java.io.File

fun openAcademicDatabase(path: String): AcademicDatabase {
    File(path).absoluteFile.parentFile?.mkdirs()
    return buildAcademicDatabase(Room.databaseBuilder<AcademicDatabase>(name = path))
}
