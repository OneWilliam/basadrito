package org.basadrito.app.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.basadrito.app.domain.auth.PortalAccess

/** El estado de cursos pertenece a este destino y se libera al abandonarlo. */
@Composable
fun DashboardRoute(
    access: PortalAccess,
    createViewModel: () -> DashboardViewModel,
    onSignOut: () -> Unit,
) {
    val owner = remember(access) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    val model: DashboardViewModel = viewModel(viewModelStoreOwner = owner) { createViewModel() }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.loadCourses() }
    DashboardScreen(state.courses, state.isLoading, state.errorMessage, { model.loadCourses() }, onSignOut)
}
