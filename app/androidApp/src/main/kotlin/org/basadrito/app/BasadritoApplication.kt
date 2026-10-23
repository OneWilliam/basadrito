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
import org.basadrito.app.database.openAcademicDatabase
import org.basadrito.app.data.diagnostics.StorageDiagnostics

class BasadritoApplication : Application() {
    private val storageDiagnostics = StorageDiagnostics { Log.w("BasadritoStorage", it.logLine()) }
    private val session by lazy { AcademicSession(AndroidSessionStore(this), diagnostics = storageDiagnostics) }
    private val cache by lazy { RoomCourseCache(openAcademicDatabase(this)) }
    val evaluationSession by lazy { AndroidIntranetEvaluationSession(session) }
    val container by lazy {
        AppContainer(
            AuthRepositoryImpl(session, AuthenticationDiagnostics { diagnostic ->
                Log.w("BasadritoAuth", diagnostic.logLine())
            }, cache = cache, storageDiagnostics = storageDiagnostics),
            CoursesRepositoryImpl(session, cache, storageDiagnostics),
            AndroidOnboardingPreferences(this),
            AndroidAppearancePreferences(this),
        )
    }
}
