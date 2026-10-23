package org.basadrito.app

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.DisposableEffect
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

/** iOS composition root. Keychain persistence and device validation are still pending. */
fun MainViewController(): platform.UIKit.UIViewController {
    val session = AcademicSession()
    val database = openAcademicDatabase()
    val cache = RoomCourseCache(database)
    val courses = CoursesRepositoryImpl(session, cache)
    val academic = AcademicRepositoryImpl(session, courses, RoomAcademicCache(database))
    val agenda = RoomAgendaCache(database)
    val personal = PersonalAgendaRepositoryImpl(session, agenda)
    val container = AppContainer(AuthRepositoryImpl(session, cache = cache), courses,
        appearance = IosAppearancePreferences(), academicRepository = academic, personalRepository = personal,
        calendarRepository = CalendarRepositoryImpl(session, academic, personal, agenda))
    return ComposeUIViewController {
        DisposableEffect(session) { onDispose { session.close(); cache.close() } }
        App(container)
    }
}
