package org.basadrito.app

import org.basadrito.app.data.repository.AuthRepositoryImpl
import org.basadrito.app.data.repository.CoursesRepositoryImpl
import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.di.AppContainer
import org.basadrito.app.database.RoomCourseCache
import org.basadrito.app.database.RoomAcademicCache
import org.basadrito.app.data.repository.AcademicRepositoryImpl
import org.basadrito.app.data.repository.PersonalAgendaRepositoryImpl
import org.basadrito.app.data.repository.CalendarRepositoryImpl
import org.basadrito.app.database.RoomAgendaCache
import org.basadrito.app.database.openAcademicDatabase
import java.io.File

/** Desktop composition root. Protected persistence is pending; access lives in memory. */
internal class DesktopAppDependencies {
    private val session = AcademicSession()
    private val directory = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Basadrito")
    private val database = openAcademicDatabase(File(directory, "academic-cache.db").absolutePath)
    private val cache = RoomCourseCache(database)
    private val courses = CoursesRepositoryImpl(session, cache)
    private val academic = AcademicRepositoryImpl(session, courses, RoomAcademicCache(database))
    private val agenda = RoomAgendaCache(database)
    private val personal = PersonalAgendaRepositoryImpl(session, agenda)
    val container = AppContainer(AuthRepositoryImpl(session, cache = cache), courses,
        appearance = DesktopAppearancePreferences(), academicRepository = academic, personalRepository = personal,
        calendarRepository = CalendarRepositoryImpl(session, academic, personal, agenda))

    fun close() { session.close(); cache.close() }
}
