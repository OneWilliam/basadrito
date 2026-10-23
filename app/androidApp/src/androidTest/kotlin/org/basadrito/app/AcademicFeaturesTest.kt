package org.basadrito.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import kotlinx.coroutines.flow.*
import org.basadrito.app.designsystem.BasadritoTheme
import org.basadrito.app.domain.academic.*
import org.basadrito.app.domain.auth.*
import org.basadrito.app.domain.calendar.*
import org.basadrito.app.domain.courses.*
import org.basadrito.app.feature.auth.AuthUiState
import org.basadrito.app.feature.calendar.CalendarViewModel
import org.basadrito.app.feature.dashboard.DashboardViewModel
import org.basadrito.app.navigation.AppNavigation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AcademicFeaturesTest {
    @get:Rule val compose = createComposeRule()
    private val items = MutableStateFlow(emptyList<PersonalItem>())
    private val personal = object : PersonalAgendaRepository {
        override fun observe() = items
        override suspend fun save(item: PersonalItem): Result<Unit> {
            val saved = item.copy(id = item.id.ifBlank { "fixture-id" })
            items.update { it.filterNot { row -> row.id == saved.id } + saved }
            return Result.success(Unit)
        }
        override suspend fun delete(id: String): Result<Unit> { items.update { it.filterNot { row -> row.id == id } }; return Result.success(Unit) }
    }
    private fun render() {
        val courses = object : CoursesRepository {
            override suspend fun getCourses() = Result.success(listOf(CourseSummary(1, "Curso fixture", "C")))
        }
        val academic = object : AcademicRepository {
            override fun observe() = flowOf(AcademicFeed(AcademicSnapshot(credits = CreditProgress(4.0, 10.0))))
            override suspend fun refresh(initial: Boolean) = Unit
        }
        val calendar = object : CalendarRepository {
            override fun observe(month: CalendarMonth) = items.map { own -> CalendarFeed(events = own.mapNotNull { item -> item.date?.let {
                CalendarEvent("personal:${item.id}", item.title, item.description, it, item.time, CalendarOrigin.PERSONAL_TASK, item.completed)
            } }, hasSchedule = true, hasTasks = true) }
            override suspend fun refresh(month: CalendarMonth, refreshTasks: Boolean) = Unit
        }
        compose.setContent { BasadritoTheme {
            AppNavigation(AuthUiState(access = PortalAccess(StudentIdentity("fixture", "Estudiante"), true)), {}, {}, {}, {},
                { DashboardViewModel(courses, academic = academic, personal = personal) }, {},
                createCalendarViewModel = { CalendarViewModel(calendar, personal) })
        } }
    }
    @Test fun dashboardShowsCreditValuesAndPersonalTasksCanBeCreatedCompletedAndEditedFromCalendar() {
        render()
        compose.onNodeWithText("4 / 10 créditos").assertIsDisplayed()
        capture("feature-home-test.png")
        compose.onNodeWithText("Agregar tarea").performScrollTo().performClick()
        compose.onNodeWithText("Título").performTextInput("Tarea propia fixture")
        compose.onNodeWithText("Fecha (AAAA-MM-DD)").performTextInput(institutionToday())
        compose.onNodeWithText("Guardar").performClick()
        compose.onNodeWithText("Tarea propia fixture").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.runOnIdle { assertTrue(items.value.single().completed) }
        compose.onNodeWithContentDescription("Calendario").performClick()
        capture("feature-calendar-test.png")
        compose.onNodeWithText("Tarea propia fixture").performScrollTo().performClick()
        compose.onNodeWithText("Título").performTextReplacement("Tarea editada fixture")
        compose.onNodeWithText("Guardar").performClick()
        compose.onNodeWithText("Tarea editada fixture").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Inicio").performClick()
        compose.onNodeWithText("Tarea editada fixture").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertTrue(items.value.single().completed) }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
