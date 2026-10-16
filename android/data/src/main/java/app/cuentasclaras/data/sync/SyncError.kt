package app.cuentasclaras.data.sync

import app.cuentasclaras.core.database.entity.OutboxEntity
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.OutboxOperation
import app.cuentasclaras.domain.model.SyncFailure

/**
 * Traduce los errores de dominio a un código corto que el outbox puede guardar
 * en la base de datos, y de vuelta. Así la UI vuelve a mostrar el error tipado
 * sin acoplarse al texto almacenado.
 */
internal object SyncError {

    private const val NETWORK = "NETWORK"
    private const val UNAUTHORIZED = "UNAUTHORIZED"
    private const val FORBIDDEN = "FORBIDDEN"
    private const val NOT_FOUND = "NOT_FOUND"
    private const val CONFLICT = "CONFLICT"
    private const val VALIDATION = "VALIDATION"
    private const val SERVER = "SERVER"
    private const val UNEXPECTED = "UNEXPECTED"

    fun codeOf(error: AppError): String = when (error) {
        AppError.Network -> NETWORK
        AppError.Unauthorized -> UNAUTHORIZED
        AppError.Forbidden -> FORBIDDEN
        AppError.NotFound -> NOT_FOUND
        is AppError.Conflict -> CONFLICT
        is AppError.Validation -> VALIDATION
        is AppError.Server -> SERVER
        is AppError.Unexpected -> UNEXPECTED
    }

    /** Human-readable text of the error to persist alongside its code. */
    fun messageOf(error: AppError): String? = when (error) {
        is AppError.Conflict -> error.message
        is AppError.Validation -> error.message
        is AppError.Server -> error.message
        is AppError.Unexpected -> error.message
        else -> null
    }

    fun of(code: String?, message: String?): AppError = when (code) {
        NETWORK -> AppError.Network
        UNAUTHORIZED -> AppError.Unauthorized
        FORBIDDEN -> AppError.Forbidden
        NOT_FOUND -> AppError.NotFound
        CONFLICT -> AppError.Conflict(message)
        VALIDATION -> AppError.Validation(message)
        SERVER -> AppError.Server(0, message)
        else -> AppError.Unexpected(message)
    }

    /**
     * Un fallo transitorio merece un reintento con backoff; uno definitivo solo
     * se resolverá con la intervención del usuario (validación, permisos…).
     */
    fun isTransient(error: AppError): Boolean = when (error) {
        AppError.Network, AppError.Unauthorized -> true
        is AppError.Server, is AppError.Unexpected -> true
        AppError.Forbidden, AppError.NotFound -> false
        is AppError.Conflict, is AppError.Validation -> false
    }
}

/** Convierte una fila fallida del outbox en el error que muestra la UI. */
internal fun OutboxEntity.toSyncFailure(): SyncFailure? {
    val parsed = OutboxOperation.fromStorage(operation) ?: return null
    return SyncFailure(
        entityId = entityId,
        operation = parsed,
        error = SyncError.of(lastErrorCode, lastError),
    )
}
