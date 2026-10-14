package app.cuentasclaras.domain.usecase.auth

import app.cuentasclaras.domain.model.AuthSession
import app.cuentasclaras.domain.repository.AuthRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/** Crea la cuenta y deja la sesión iniciada. */
class RegisterUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        displayName: String,
        defaultCurrency: String,
    ): AppResult<AuthSession> = repository.register(
        email = email.trim().lowercase(),
        password = password,
        displayName = displayName.trim(),
        defaultCurrency = defaultCurrency.trim().uppercase(),
    )
}
