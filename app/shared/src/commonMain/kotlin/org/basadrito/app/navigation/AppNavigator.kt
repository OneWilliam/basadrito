package org.basadrito.app.navigation

import androidx.navigation3.runtime.NavKey
import org.basadrito.app.feature.auth.AuthUiState
import org.basadrito.app.domain.auth.AccessMode

/** Mutates Navigation 3's observable stack. Inicio remains the authenticated root. */
internal class AppNavigator(private val backStack: MutableList<NavKey>) {
    val current: AppDestination get() = backStack.last() as AppDestination

    fun select(section: MainSection) {
        val destination = section.destination ?: return
        if (current == destination) return
        val existing = backStack.indexOf(destination)
        if (existing >= 0) {
            while (backStack.lastIndex > existing) backStack.removeAt(backStack.lastIndex)
        } else backStack.add(destination)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun reset() {
        backStack.clear()
        backStack.add(AppDestination.Home)
    }
}

internal fun canShowAcademicNavigation(state: AuthUiState): Boolean =
    !state.isRestoring && state.access?.let {
        (it.intranetAvailable || it.mode == AccessMode.LOCAL_ONLY) && it.intranetRequirement == null
    } == true
