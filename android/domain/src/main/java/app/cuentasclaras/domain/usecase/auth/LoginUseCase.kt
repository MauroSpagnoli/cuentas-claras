package app.cuentasclaras.domain.usecase.auth

import app.cuentasclaras.domain.model.AuthSession
import app.cuentasclaras.domain.repository.AuthRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/** Autentica con email/contraseña. Normaliza el email antes de la llamada. */
class LoginUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): AppResult<AuthSession> =
        repository.login(email.trim().lowercase(), password)
}
