package org.basadrito.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import org.basadrito.app.data.repository.AuthenticationDiagnostics
import org.basadrito.app.data.repository.AuthRepositoryImpl
import org.basadrito.app.data.repository.CoursesRepositoryImpl
import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.data.session.AndroidSessionStore
import org.basadrito.app.di.AppContainer
import org.basadrito.app.data.intranet.AndroidIntranetEvaluationSession

class BasadritoApplication : Application() {
    private val session by lazy { AcademicSession(AndroidSessionStore(this)) }
    val evaluationSession by lazy { AndroidIntranetEvaluationSession(session) }
    val container by lazy {
        AppContainer(
            AuthRepositoryImpl(session, AuthenticationDiagnostics { diagnostic ->
                if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    Log.w("BasadritoAuth", diagnostic.logLine())
                }
            }),
            CoursesRepositoryImpl(session),
            AndroidOnboardingPreferences(this),
        )
    }
}
