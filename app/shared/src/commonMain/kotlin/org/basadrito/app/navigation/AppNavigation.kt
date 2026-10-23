package org.basadrito.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.basadrito.app.domain.auth.PortalAccess
import org.basadrito.app.feature.auth.AuthUiState
import org.basadrito.app.feature.auth.LoginScreen
import org.basadrito.app.feature.dashboard.DashboardRoute
import org.basadrito.app.feature.dashboard.DashboardViewModel
import org.basadrito.app.feature.settings.SettingsScreen
import org.basadrito.app.designsystem.ThemeMode
import org.basadrito.app.feature.dashboard.HomeRoute
import org.basadrito.app.feature.calendar.CalendarRoute
import org.basadrito.app.feature.calendar.CalendarViewModel

// Explicit serializers also work on non-JVM targets, where route reflection is unavailable.
private val routeConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(AppDestination.Home::class, AppDestination.Home.serializer())
            subclass(AppDestination.Courses::class, AppDestination.Courses.serializer())
            subclass(AppDestination.Settings::class, AppDestination.Settings.serializer())
            subclass(AppDestination.Calendar::class, AppDestination.Calendar.serializer())
        }
    }
}

/** Authentication is authoritative: a saved navigation stack never grants access. */
@Composable
fun AppNavigation(
    state: AuthUiState,
    onUsernameChange: (String) -> Unit,
    onMoodlePasswordChange: (String) -> Unit,
    onIntranetPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    createDashboardViewModel: () -> DashboardViewModel,
    onSignOut: () -> Unit,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeChange: (ThemeMode) -> Unit = {},
    onReconnect: () -> Unit = {},
    createCalendarViewModel: () -> CalendarViewModel = { CalendarViewModel() },
) {
    val access = state.access
    if (access == null) {
        if (!state.isRestoring) LoginScreen(state, onUsernameChange, onMoodlePasswordChange, onIntranetPasswordChange, onSignIn)
        return
    }
    if (!canShowAcademicNavigation(state)) return
    key(access.student.username) {
        AcademicNavigation(access, createDashboardViewModel, onSignOut, themeMode, onThemeChange, onReconnect, createCalendarViewModel)
    }
}

@Composable
private fun AcademicNavigation(access: PortalAccess, createDashboardViewModel: () -> DashboardViewModel, onSignOut: () -> Unit,
    themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit, onReconnect: () -> Unit, createCalendarViewModel: () -> CalendarViewModel) {
    val backStack = rememberNavBackStack(routeConfiguration, AppDestination.Home)
    val navigator = remember(backStack) { AppNavigator(backStack) }
    MainNavigationScaffold(navigator.current, navigator::select) {
        NavDisplay(
            backStack = backStack,
            onBack = navigator::back,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<AppDestination.Home> { HomeRoute(access, createDashboardViewModel) { navigator.select(MainSection.COURSES) } }
                entry<AppDestination.Courses> { DashboardRoute(access, createDashboardViewModel, navigator::back) }
                entry<AppDestination.Calendar> { CalendarRoute(createCalendarViewModel, navigator::back) }
                entry<AppDestination.Settings> {
                    SettingsScreen(access, navigator::back, themeMode, onThemeChange, onReconnect) {
                        navigator.reset()
                        onSignOut()
                    }
                }
            },
        )
    }
}
