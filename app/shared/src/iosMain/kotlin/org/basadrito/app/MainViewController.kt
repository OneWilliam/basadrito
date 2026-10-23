package org.basadrito.app

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.DisposableEffect
import org.basadrito.app.data.repository.AuthRepositoryImpl
import org.basadrito.app.data.repository.CoursesRepositoryImpl
import org.basadrito.app.data.session.AcademicSession
import org.basadrito.app.di.AppContainer
import org.basadrito.app.database.RoomCourseCache
import org.basadrito.app.database.openAcademicDatabase

/** iOS composition root. Keychain persistence and device validation are still pending. */
fun MainViewController(): platform.UIKit.UIViewController {
    val session = AcademicSession()
    val cache = RoomCourseCache(openAcademicDatabase())
    val container = AppContainer(AuthRepositoryImpl(session, cache = cache), CoursesRepositoryImpl(session, cache),
        appearance = IosAppearancePreferences())
    return ComposeUIViewController {
        DisposableEffect(session) { onDispose { session.close(); cache.close() } }
        App(container)
    }
}
