package app.cuentasclaras.domain.usecase.auth

import app.cuentasclaras.domain.model.AuthSession
import app.cuentasclaras.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observa la sesión activa (persistente entre reinicios). */
class ObserveSessionUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    operator fun invoke(): Flow<AuthSession?> = repository.observeSession()
}
