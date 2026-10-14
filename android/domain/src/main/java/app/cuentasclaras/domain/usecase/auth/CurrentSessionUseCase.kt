package app.cuentasclaras.domain.usecase.auth

import app.cuentasclaras.domain.model.AuthSession
import app.cuentasclaras.domain.repository.AuthRepository
import javax.inject.Inject

/** Lectura puntual de la sesión, para decidir la ruta inicial. */
class CurrentSessionUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): AuthSession? = repository.currentSession()
}
