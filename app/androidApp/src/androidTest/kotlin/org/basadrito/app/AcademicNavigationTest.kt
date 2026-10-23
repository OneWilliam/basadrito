package org.basadrito.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import org.basadrito.app.designsystem.BasadritoTheme
import org.basadrito.app.designsystem.ThemeMode
import org.basadrito.app.domain.auth.PortalAccess
import org.basadrito.app.domain.auth.StudentIdentity
import org.basadrito.app.domain.courses.CourseSummary
import org.basadrito.app.domain.courses.CoursesRepository
import org.basadrito.app.domain.courses.CourseSnapshot
import org.basadrito.app.domain.courses.CourseSource
import org.basadrito.app.domain.courses.CourseCacheWarning
import org.basadrito.app.feature.auth.AuthUiState
import org.basadrito.app.feature.dashboard.DashboardViewModel
import org.basadrito.app.navigation.AppNavigation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A standalone Compose host with fictitious data; never opens the university session or clients. */
@RunWith(AndroidJUnit4::class)
class AcademicNavigationTest {
    @get:Rule val compose = createComposeRule()

    private var authState by mutableStateOf(AuthUiState(access = fixtureAccess))
    private var createdModels = 0
    private var courseRequests = 0
    private var themeMode by mutableStateOf(ThemeMode.LIGHT)
    private var cacheWarning: CourseCacheWarning? = null
    private val courses = object : CoursesRepository {
        override suspend fun getSnapshot(refresh: Boolean): Result<CourseSnapshot?> =
            if (!refresh) Result.success(null)
            else getCourses().map { CourseSnapshot(it, CourseSource.REMOTE, cacheWarning = cacheWarning) }
        override suspend fun getCourses(): Result<List<CourseSummary>> {
            courseRequests++
            return Result.success((1..40).map { CourseSummary(it, "Curso ficticio $it", "") })
        }
    }

    @Test
    fun failedSaveShowsOfflineWarningWhileCoursesRemainAvailable() {
        cacheWarning = CourseCacheWarning.WRITE_FAILED
        render()
        compose.onNodeWithText("Los cursos se pueden consultar, pero no quedaron guardados para usarlos sin conexión.").assertIsDisplayed()
        compose.onNodeWithText("Curso ficticio 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cursos").performClick()
        compose.onNodeWithText("Los cursos se pueden consultar, pero no quedaron guardados para usarlos sin conexión.").assertIsDisplayed()
        compose.onNodeWithText("Curso ficticio 1").assertIsDisplayed()
    }

    private fun render() {
        compose.setContent { NavigationContent() }
    }

    @Test
    fun scrollingCoursesAndSettingsKeepsHeaderControlsInPlace() {
        render()
        saveScreenshot("navigation-home-test.png")
        compose.onNodeWithContentDescription("Cursos").performClick()
        saveScreenshot("navigation-courses-top-test.png")
        val back = compose.onNodeWithContentDescription("Volver").fetchSemanticsNode().boundsInRoot
        val bell = compose.onNodeWithContentDescription("Notificaciones, próximamente").fetchSemanticsNode().boundsInRoot
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Curso ficticio 21"))
        compose.onNodeWithText("Curso ficticio 21").assertIsDisplayed()
        assertEquals(back, compose.onNodeWithContentDescription("Volver").fetchSemanticsNode().boundsInRoot)
        assertEquals(bell, compose.onNodeWithContentDescription("Notificaciones, próximamente").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("Ajustes").performClick()
        saveScreenshot("navigation-settings-top-test.png")
        val settingsBack = compose.onNodeWithContentDescription("Volver").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Cerrar sesión").performScrollTo().assertIsDisplayed()
        assertEquals(settingsBack, compose.onNodeWithContentDescription("Volver").fetchSemanticsNode().boundsInRoot)
    }

    @androidx.compose.runtime.Composable
    private fun NavigationContent() {
        BasadritoTheme(themeMode) {
            AppNavigation(authState, {}, {}, {}, {}, {
                createdModels++
                DashboardViewModel(courses)
            }, { authState = AuthUiState() }, themeMode, { themeMode = it })
        }
    }

    @Test
    fun settingsAndSystemBackRetainCourseModelAndScroll() {
        render()
        compose.onNodeWithContentDescription("Cursos").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Curso ficticio 21"))
        compose.onNodeWithText("Curso ficticio 21").assertIsDisplayed()
        saveScreenshot("navigation-courses-test.png")
        compose.onNodeWithContentDescription("Ajustes").performClick()
        compose.onNodeWithContentDescription("Ajustes").assertIsSelected()
        compose.onNodeWithText("Cerrar sesión").performScrollTo().assertIsDisplayed()
        saveScreenshot("navigation-settings-test.png")
        pressBack()
        compose.onNodeWithContentDescription("Cursos").assertIsSelected()
        compose.onNodeWithText("Curso ficticio 21").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, createdModels)
            assertEquals(2, courseRequests)
        }
    }

    @Test
    fun logoutAndAnotherLoginStartAtHomeWithANewModel() {
        render()
        compose.onNodeWithContentDescription("Ajustes").performClick()
        compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
        compose.onNodeWithText("Inicia sesión").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cursos").assertDoesNotExist()
        compose.runOnIdle { authState = AuthUiState(access = fixtureAccess.copy(student = StudentIdentity("other-fixture", "Otra cuenta ficticia"))) }
        compose.onNodeWithContentDescription("Inicio").assertIsSelected()
        compose.onNodeWithText("Hola, Otra").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, createdModels) }
    }

    @Test
    fun savedNavigationRestoresSettingsOnlyAfterAccessIsAllowed() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { NavigationContent() }
        compose.onNodeWithContentDescription("Ajustes").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Ajustes").assertIsSelected()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithContentDescription("Inicio").assertIsSelected()
        compose.onNodeWithContentDescription("Herramientas, próximamente").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Calendario").assertIsEnabled()
    }

    @Test
    fun changingAppearanceKeepsDestinationAndCourseState() {
        render()
        compose.onNodeWithContentDescription("Cursos").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Curso ficticio 21"))
        compose.onNodeWithContentDescription("Ajustes").performClick()
        compose.onNodeWithText("Modo oscuro").performScrollTo().performClick()
        compose.onNodeWithText("Oscuro").performClick()
        compose.runOnIdle { assertEquals(ThemeMode.DARK, themeMode) }
        compose.onNodeWithContentDescription("Ajustes").assertIsSelected()
        saveScreenshot("appearance-dark-test.png")
        pressBack()
        compose.onNodeWithText("Curso ficticio 21").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, createdModels); assertEquals(2, courseRequests) }
        saveScreenshot("courses-dark-test.png")
        compose.onNodeWithContentDescription("Ajustes").performClick()
        compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
        compose.onNodeWithText("Inicia sesión").assertIsDisplayed()
        saveScreenshot("login-dark-test.png")
    }

    private companion object {
        val fixtureAccess = PortalAccess(StudentIdentity("fixture-student", "Estudiante ficticio"), intranetAvailable = true, sessionRemembered = true)
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.cacheDir, name).outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
