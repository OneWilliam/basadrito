package org.basadrito.app

import android.app.Application
import android.util.Log
import org.basadrito.app.data.repository.AuthenticationDiagnostics
import org.basadrito.app.data.repository.AuthRepositoryImpl
import org.basadrito.app.data.repository.CoursesRepositoryImpl
import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.data.session.AndroidSessionStore
import org.basadrito.app.di.AppContainer
import org.basadrito.app.data.intranet.AndroidIntranetEvaluationSession
import org.basadrito.app.database.RoomCourseCache
import org.basadrito.app.database.RoomAcademicCache
import org.basadrito.app.data.repository.AcademicRepositoryImpl
import org.basadrito.app.data.repository.PersonalAgendaRepositoryImpl
import org.basadrito.app.data.repository.CalendarRepositoryImpl
import org.basadrito.app.database.RoomAgendaCache
import org.basadrito.app.database.openAcademicDatabase
import org.basadrito.app.data.diagnostics.StorageDiagnostics

class BasadritoApplication : Application() {
    private val storageDiagnostics = StorageDiagnostics { Log.w("BasadritoStorage", it.logLine()) }
    private val session by lazy { AcademicSession(AndroidSessionStore(this), diagnostics = storageDiagnostics) }
    private val database by lazy { openAcademicDatabase(this) }
    private val cache by lazy { RoomCourseCache(database) }
    private val courses by lazy { CoursesRepositoryImpl(session, cache, storageDiagnostics) }
    private val academic by lazy { AcademicRepositoryImpl(session, courses, RoomAcademicCache(database), storageDiagnostics) }
    private val agenda by lazy { RoomAgendaCache(database) }
    private val personal by lazy { PersonalAgendaRepositoryImpl(session, agenda, storageDiagnostics) }
    private val calendar by lazy { CalendarRepositoryImpl(session, academic, personal, agenda, storageDiagnostics) }
    val evaluationSession by lazy { AndroidIntranetEvaluationSession(session) }
    val container by lazy {
        AppContainer(
            AuthRepositoryImpl(session, AuthenticationDiagnostics { diagnostic ->
                Log.w("BasadritoAuth", diagnostic.logLine())
            }, cache = cache, storageDiagnostics = storageDiagnostics),
            courses,
            AndroidOnboardingPreferences(this),
            AndroidAppearancePreferences(this),
            academic, personal, calendar,
        )
    }
}
