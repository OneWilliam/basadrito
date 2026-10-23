package org.basadrito.app

import org.basadrito.app.data.repository.AuthRepositoryImpl
import org.basadrito.app.data.repository.CoursesRepositoryImpl
import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.di.AppContainer
import org.basadrito.app.database.RoomCourseCache
import org.basadrito.app.database.openAcademicDatabase
import java.io.File

/** Desktop composition root. Protected persistence is pending; access lives in memory. */
internal class DesktopAppDependencies {
    private val session = AcademicSession()
    private val directory = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Basadrito")
    private val cache = RoomCourseCache(openAcademicDatabase(File(directory, "academic-cache.db").absolutePath))
    val container = AppContainer(AuthRepositoryImpl(session, cache = cache), CoursesRepositoryImpl(session, cache),
        appearance = DesktopAppearancePreferences())

    fun close() { session.close(); cache.close() }
}
