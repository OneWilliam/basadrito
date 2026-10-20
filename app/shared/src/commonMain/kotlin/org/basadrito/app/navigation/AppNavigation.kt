package org.basadrito.app.navigation

import androidx.compose.runtime.Composable
import org.basadrito.app.feature.auth.AuthUiState
import org.basadrito.app.feature.auth.LoginScreen
import org.basadrito.app.feature.dashboard.DashboardRoute
import org.basadrito.app.feature.dashboard.DashboardViewModel

/** El destino depende del estado autenticado; no conserva otra copia de la sesión. */
@Composable
fun AppNavigation(
    state: AuthUiState,
    onUsernameChange: (String) -> Unit,
    onMoodlePasswordChange: (String) -> Unit,
    onIntranetPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    createDashboardViewModel: () -> DashboardViewModel,
    onSignOut: () -> Unit,
) {
    val access = state.access
    if (access == null) {
        LoginScreen(state, onUsernameChange, onMoodlePasswordChange, onIntranetPasswordChange, onSignIn)
    } else {
        DashboardRoute(access, createDashboardViewModel, onSignOut)
    }
}
