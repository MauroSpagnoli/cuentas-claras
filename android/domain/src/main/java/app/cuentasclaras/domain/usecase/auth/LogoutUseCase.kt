package app.cuentasclaras.domain.usecase.auth

import app.cuentasclaras.domain.repository.AuthRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/** Revoca el refresh token en el servidor y limpia la sesión local. */
class LogoutUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> = repository.logout()
}
