package org.basadrito.app.database

import android.content.Context
import androidx.room.Room
import java.io.File

/** Private app storage, excluded from Android backup and device transfer. */
fun openAcademicDatabase(context: Context, name: String = "academic-cache.db"): AcademicDatabase {
    require(name.matches(Regex("[A-Za-z0-9._-]+")))
    return buildAcademicDatabase(Room.databaseBuilder<AcademicDatabase>(context.applicationContext,
        File(context.applicationContext.noBackupFilesDir, name).absolutePath))
}
