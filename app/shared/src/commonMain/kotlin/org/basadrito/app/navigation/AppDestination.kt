package org.basadrito.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Only implemented screens are restorable destinations. No account secrets belong in a route. */
@Serializable
sealed interface AppDestination : NavKey {
    @Serializable data object Home : AppDestination
    @Serializable data object Courses : AppDestination
    @Serializable data object Settings : AppDestination
}

/** A future sidebar can consume these same sections without changing the stack. */
internal enum class MainSection(val label: String, val destination: AppDestination?) {
    HOME("Inicio", AppDestination.Home),
    COURSES("Cursos", AppDestination.Courses),
    TOOLS("Herramientas", null),
    CALENDAR("Calendario", null),
    SETTINGS("Ajustes", AppDestination.Settings),
}
