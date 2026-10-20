package org.basadrito.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.basadrito.app.designsystem.BasadritoTheme
import org.basadrito.app.di.AppContainer
import org.basadrito.app.feature.auth.AuthViewModel
import org.basadrito.app.navigation.AppNavigation
import org.basadrito.app.feature.onboarding.OnboardingScreen
import org.basadrito.app.feature.onboarding.OnboardingViewModel
import org.basadrito.app.domain.auth.PortalAccess
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalUriHandler

@Composable
fun App(
    container: AppContainer,
    intranetEvaluationContent: (@Composable (onConfirmed: (PortalAccess) -> Unit, onSignOut: () -> Unit) -> Unit)? = null,
) {
    val onboarding: OnboardingViewModel = viewModel { container.createOnboardingViewModel() }
    val onboardingStep by onboarding.step.collectAsStateWithLifecycle()
    val auth: AuthViewModel = viewModel { container.createAuthViewModel() }
    val state by auth.state.collectAsStateWithLifecycle()
    BasadritoTheme {
        if (onboardingStep != OnboardingViewModel.COMPLETE) {
            OnboardingScreen(onboardingStep, onboarding::next, onboarding::back)
            return@BasadritoTheme
        }
        if (state.access?.intranetRequirement != null) {
            if (intranetEvaluationContent != null) {
                intranetEvaluationContent(auth::acceptConfirmedIntranetAccess, auth::signOut)
            } else {
                val uriHandler = LocalUriHandler.current
                AlertDialog(onDismissRequest = {}, title = { Text("Evaluación docente pendiente") },
                    text = { Text("Completa la evaluación en la Intranet y vuelve a iniciar sesión.") },
                    confirmButton = { TextButton(onClick = { uriHandler.openUri("https://intranet.unjbg.edu.pe/alumno/evaluacion-docente") }) { Text("Abrir en navegador") } },
                    dismissButton = { TextButton(onClick = auth::signOut) { Text("Volver al login") } })
            }
            return@BasadritoTheme
        }
        AppNavigation(
            state = state,
            onUsernameChange = auth::setUsername,
            onMoodlePasswordChange = auth::setMoodlePassword,
            onIntranetPasswordChange = auth::setIntranetPassword,
            onSignIn = { auth.signIn() },
            createDashboardViewModel = container::createDashboardViewModel,
            onSignOut = auth::signOut,
        )
    }
}
