package app.cuentasclaras.feature.auth

import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.validation.AuthField
import app.cuentasclaras.domain.validation.ValidationIssue

/** Modo del formulario de autenticación. */
enum class AuthMode { LOGIN, REGISTER }

/** Estado inmutable que la UI renderiza (MVI). */
data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val defaultCurrency: String = "EUR",
    val passwordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val fieldIssues: Map<AuthField, ValidationIssue> = emptyMap(),
    val error: AppError? = null,
) {
    val isRegister: Boolean get() = mode == AuthMode.REGISTER

    val canSubmit: Boolean
        get() = !isSubmitting &&
            email.isNotBlank() &&
            password.isNotBlank() &&
            (!isRegister || displayName.isNotBlank())
}

/** Eventos que la UI envía al ViewModel. */
sealed interface AuthEvent {
    data class EmailChanged(val value: String) : AuthEvent
    data class PasswordChanged(val value: String) : AuthEvent
    data class DisplayNameChanged(val value: String) : AuthEvent
    data class CurrencyChanged(val value: String) : AuthEvent
    data object TogglePasswordVisibility : AuthEvent
    data object Submit : AuthEvent
    data object DismissError : AuthEvent
}
