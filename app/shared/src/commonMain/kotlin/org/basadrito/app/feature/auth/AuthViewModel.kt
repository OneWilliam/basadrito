package org.basadrito.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.basadrito.app.domain.auth.AuthRepository
import org.basadrito.app.domain.auth.PortalAccess
import kotlin.coroutines.cancellation.CancellationException

class AuthViewModel(
    private val auth: AuthRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val operationScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(AuthUiState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0L

    fun setUsername(value: String) = edit { copy(username = value) }
    fun setMoodlePassword(value: String) = edit { copy(moodlePassword = value) }
    fun setIntranetPassword(value: String) = edit { copy(intranetPassword = value) }

    private fun edit(change: AuthUiState.() -> AuthUiState) {
        if (!state.value.isLoading && state.value.access == null) {
            mutableState.update { it.change().copy(errorMessage = null) }
        }
    }

    fun signIn(): Job? {
        val input = state.value
        if (input.isLoading || input.access != null) return null
        if (input.username.isBlank() || input.moodlePassword.isBlank()) {
            mutableState.update { it.copy(errorMessage = "Ingresa tu código y contraseña del aula virtual.") }
            return null
        }
        mutableState.update { it.copy(isLoading = true, loadingMessage = "Ingresando…", errorMessage = null) }
        val request = ++generation
        return operationScope.launch {
            try {
                auth.signIn(input.username.trim(), input.moodlePassword, input.intranetPassword).fold(
                    onSuccess = { access ->
                        updateFor(request) {
                            it.copy(access = access, moodlePassword = "", intranetPassword = "")
                        }
                    },
                    onFailure = { error -> updateFor(request) { it.copy(errorMessage = error.message ?: "No se pudo iniciar sesión.") } },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateFor(request) { it.copy(errorMessage = "No se pudo iniciar sesión. Intenta nuevamente.") }
            } finally {
                updateFor(request) { it.copy(isLoading = false, loadingMessage = "") }
            }
        }.also { operation = it }
    }

    private fun updateFor(request: Long, change: (AuthUiState) -> AuthUiState) {
        if (request == generation) mutableState.update(change)
    }

    fun signOut() {
        generation++
        operation?.cancel()
        auth.signOut()
        mutableState.value = AuthUiState()
    }

    fun acceptConfirmedIntranetAccess(access: PortalAccess) {
        val previous = state.value.access ?: return
        if (previous.intranetRequirement == null || !access.intranetAvailable ||
            access.intranetRequirement != null || access.student != previous.student) return
        mutableState.update { it.copy(access = access) }
    }

    override fun onCleared() {
        generation++
        operation?.cancel()
    }
}
