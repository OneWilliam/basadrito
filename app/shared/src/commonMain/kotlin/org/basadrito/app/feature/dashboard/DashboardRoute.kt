package org.basadrito.app.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.basadrito.app.domain.auth.PortalAccess

/** Navigation 3 retains this model while its entry exists and clears it on logout/removal. */
@Composable
fun DashboardRoute(
    access: PortalAccess,
    createViewModel: () -> DashboardViewModel,
    onBack: () -> Unit = {},
) {
    val model: DashboardViewModel = viewModel { createViewModel() }
    val state by model.state.collectAsStateWithLifecycle()
    val academic by model.academicState.collectAsStateWithLifecycle()
    DashboardScreen(state.courses, state.isLoading, state.errorMessage, { model.loadCourses() },
        sessionRemembered = access.sessionRemembered, onBack = onBack, snapshotState = state,
        localOnly = access.mode == org.basadrito.app.domain.auth.AccessMode.LOCAL_ONLY, academic = academic)
}
