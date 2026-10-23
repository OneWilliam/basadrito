package org.basadrito.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.json.Json
import org.basadrito.app.domain.auth.IntranetRequirement
import org.basadrito.app.domain.auth.PortalAccess
import org.basadrito.app.domain.auth.StudentIdentity
import org.basadrito.app.feature.auth.AuthUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigatorTest {
    @Test
    fun selectingSectionsAndBackNeverDuplicateOrRemoveRoot() {
        val stack = mutableListOf<NavKey>(AppDestination.Courses)
        val navigator = AppNavigator(stack)
        navigator.select(MainSection.COURSES)
        navigator.select(MainSection.SETTINGS)
        navigator.select(MainSection.SETTINGS)
        assertEquals(listOf<NavKey>(AppDestination.Courses, AppDestination.Settings), stack)
        navigator.back()
        navigator.back()
        assertEquals(listOf<NavKey>(AppDestination.Courses), stack)
        navigator.select(MainSection.SETTINGS)
        navigator.select(MainSection.COURSES)
        assertEquals(listOf<NavKey>(AppDestination.Courses), stack)
    }

    @Test
    fun unavailableSectionsDoNotNavigateAndLogoutResetsPrivateHistory() {
        val stack = mutableListOf<NavKey>(AppDestination.Courses)
        val navigator = AppNavigator(stack)
        MainSection.entries.filter { it.destination == null }.forEach(navigator::select)
        assertEquals(listOf<NavKey>(AppDestination.Courses), stack)
        navigator.select(MainSection.SETTINGS)
        navigator.reset()
        assertEquals(AppDestination.Home, navigator.current)
        assertEquals(1, stack.size)
    }

    @Test
    fun restoringOrPendingAccessCannotOpenPrivateDestinations() {
        val access = PortalAccess(StudentIdentity("fixture", "Estudiante ficticio"), intranetAvailable = true)
        assertFalse(canShowAcademicNavigation(AuthUiState()))
        assertFalse(canShowAcademicNavigation(AuthUiState(access = access, isRestoring = true)))
        assertFalse(canShowAcademicNavigation(AuthUiState(access = access.copy(intranetAvailable = false))))
        assertFalse(canShowAcademicNavigation(AuthUiState(access = access.copy(intranetRequirement = IntranetRequirement.TEACHER_EVALUATION))))
        assertTrue(canShowAcademicNavigation(AuthUiState(access = access)))
    }

    @Test
    fun typedRoutesRoundTripWithoutReflectionOrSessionMaterial() {
        val routes: List<AppDestination> = listOf(AppDestination.Home, AppDestination.Courses, AppDestination.Settings)
        val encoded = Json.encodeToString(routes)
        assertEquals(routes, Json.decodeFromString<List<AppDestination>>(encoded))
        assertFalse(encoded.contains("token", ignoreCase = true))
    }
}
