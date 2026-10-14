package app.cuentasclaras.domain.repository

import app.cuentasclaras.domain.model.AuthSession
import app.cuentasclaras.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de autenticación: sesión persistente + operaciones remotas.
 */
interface AuthRepository {

    /** Sesión actual como flujo; `null` cuando no hay usuario autenticado. */
    fun observeSession(): Flow<AuthSession?>

    /** Lectura puntual de la sesión (para decidir la navegación inicial). */
    suspend fun currentSession(): AuthSession?

    suspend fun login(email: String, password: String): AppResult<AuthSession>

    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        defaultCurrency: String,
    ): AppResult<AuthSession>

    /** Cierra sesión en el servidor (best effort) y limpia el almacenamiento local. */
    suspend fun logout(): AppResult<Unit>
}
