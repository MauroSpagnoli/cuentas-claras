package app.cuentasclaras.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.usecase.auth.LoginUseCase
import app.cuentasclaras.domain.usecase.auth.RegisterUseCase
import app.cuentasclaras.domain.validation.AuthValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel MVI del formulario de autenticación.
 *
 * El modo (login/registro) llega como argumento de navegación, por lo que cada
 * ruta tiene su propia instancia. Tras un login correcto no se emite navegación:
 * al persistirse la sesión cambia el flujo observado por la navegación raíz y
 * la app conmuta de grafo.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val login: LoginUseCase,
    private val register: RegisterUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val initialMode = when (savedStateHandle.get<String>(AuthRoutes.ARG_MODE)) {
        AuthRoutes.MODE_REGISTER -> AuthMode.REGISTER
        else -> AuthMode.LOGIN
    }

    private val _state = MutableStateFlow(AuthUiState(mode = initialMode))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.EmailChanged -> updateInput { it.copy(email = event.value) }
            is AuthEvent.PasswordChanged -> updateInput { it.copy(password = event.value) }
            is AuthEvent.DisplayNameChanged -> updateInput { it.copy(displayName = event.value) }
            is AuthEvent.CurrencyChanged -> updateInput { it.copy(defaultCurrency = event.value) }
            AuthEvent.TogglePasswordVisibility ->
                _state.update { it.copy(passwordVisible = !it.passwordVisible) }
            AuthEvent.Submit -> submit()
            AuthEvent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun updateInput(transform: (AuthUiState) -> AuthUiState) {
        _state.update { transform(it).copy(error = null, fieldIssues = emptyMap()) }
    }

    private fun submit() {
        val current = _state.value
        val issues = if (current.isRegister) {
            AuthValidator.validateRegister(
                email = current.email,
                password = current.password,
                displayName = current.displayName,
                defaultCurrency = current.defaultCurrency,
            )
        } else {
            AuthValidator.validateLogin(current.email, current.password)
        }
        if (issues.isNotEmpty()) {
            _state.update { it.copy(fieldIssues = issues) }
            return
        }

        _state.update { it.copy(isSubmitting = true, fieldIssues = emptyMap(), error = null) }
        viewModelScope.launch {
            val result = if (current.isRegister) {
                register(current.email, current.password, current.displayName, current.defaultCurrency)
            } else {
                login(current.email, current.password)
            }
            when (result) {
                is AppResult.Success -> _state.update { it.copy(isSubmitting = false) }
                is AppResult.Failure -> _state.update {
                    it.copy(isSubmitting = false, error = result.error)
                }
            }
        }
    }
}
