package app.cuentasclaras.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuentasclaras.domain.model.AuthSession
import app.cuentasclaras.domain.usecase.auth.LogoutUseCase
import app.cuentasclaras.domain.usecase.auth.ObserveSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val session: AuthSession) : SessionState
}

/**
 * Estado de sesión global. La navegación raíz lo observa para decidir qué grafo
 * mostrar (auth vs. área autenticada) y así restaurar la sesión al arrancar.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    val state: StateFlow<SessionState> = observeSession()
        .map { session ->
            if (session == null) SessionState.LoggedOut else SessionState.LoggedIn(session)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SessionState.Loading,
        )

    fun signOut() {
        viewModelScope.launch { logoutUseCase() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
