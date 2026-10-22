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
import org.basadrito.app.domain.auth.SessionRecoveryException
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

    init { restoreSession() }

    fun restoreSession(): Job? {
        if (state.value.isLoading || state.value.access != null) return null
        val request = ++generation
        mutableState.value = AuthUiState(isRestoring = true, isLoading = true, loadingMessage = "Recuperando acceso…")
        return operationScope.launch {
            try {
                auth.restoreSession().fold(
                    onSuccess = { access -> updateFor(request) { it.copy(access = access, isRestoring = false) } },
                    onFailure = { error -> updateFor(request) {
                        val recovery = error as? SessionRecoveryException
                        it.copy(isRestoring = recovery?.canRetry ?: true,
                            errorMessage = recovery?.message ?: "No se pudo recuperar el acceso guardado. Intenta nuevamente.")
                    } },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateFor(request) { it.copy(errorMessage = "No se pudo recuperar el acceso guardado. Intenta nuevamente.") }
            } finally {
                updateFor(request) { it.copy(isLoading = false, loadingMessage = "") }
            }
        }.also { operation = it }
    }

    fun setUsername(value: String) = edit { copy(username = value) }
    fun setMoodlePassword(value: String) = edit { copy(moodlePassword = value) }
    fun setIntranetPassword(value: String) = edit { copy(intranetPassword = value) }

    private fun edit(change: AuthUiState.() -> AuthUiState) {
        if (!state.value.isLoading && !state.value.isRestoring && state.value.access == null) {
            mutableState.update { it.change().copy(errorMessage = null) }
        }
    }

    fun signIn(): Job? {
        val input = state.value
        if (input.isLoading || input.isRestoring || input.access != null) return null
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
        try {
            auth.signOut()
            mutableState.value = AuthUiState()
        } catch (_: Exception) {
            mutableState.value = AuthUiState(errorMessage = "No se pudo eliminar el acceso guardado. Intenta cerrar sesión nuevamente.", isRestoring = true)
        }
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
