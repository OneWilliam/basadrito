package org.basadrito.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.basadrito.app.designsystem.BasadritoTheme
import org.basadrito.app.feature.auth.AuthUiState
import org.basadrito.app.feature.auth.LoginScreen
import org.basadrito.app.feature.evaluation.IntranetEvaluationPanel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val dependencies = application as BasadritoApplication
            App(dependencies.container) { onConfirmed, onSignOut ->
                IntranetEvaluationPanel(
                    prepareBrowser = dependencies.evaluationSession::prepareBrowser,
                    confirmAccess = dependencies.evaluationSession::confirmAccess,
                    clearBrowser = dependencies.evaluationSession::clearBrowser,
                    onConfirmed = onConfirmed,
                    onSignOut = onSignOut,
                )
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    BasadritoTheme {
        LoginScreen(
            state = AuthUiState(),
            onUsernameChange = {},
            onMoodlePasswordChange = {},
            onIntranetPasswordChange = {},
            onLogin = {},
        )
    }
}
