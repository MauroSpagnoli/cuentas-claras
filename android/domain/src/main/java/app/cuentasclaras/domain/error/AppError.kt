package app.cuentasclaras.domain.error

/**
 * Errores de dominio normalizados. La capa de red traduce los problemas RFC 7807
 * del backend a estos tipos para que la capa de presentación no dependa de HTTP.
 */
sealed interface AppError {

    /** No hubo conexión o la petición no llegó a completarse. */
    data object Network : AppError

    /** 401: token ausente, inválido o expirado (y no se pudo refrescar). */
    data object Unauthorized : AppError

    /** 403: autenticado pero sin permisos sobre el recurso. */
    data object Forbidden : AppError

    /** 404: el recurso no existe o no es visible para el usuario. */
    data object NotFound : AppError

    /** 409: conflicto de versión o de estado (p. ej. email ya registrado). */
    data class Conflict(val message: String?) : AppError

    /** 400/422: errores de validación, opcionalmente por campo. */
    data class Validation(
        val message: String?,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : AppError

    /** 5xx: fallo del servidor. */
    data class Server(val status: Int, val message: String?) : AppError

    /** Cualquier otra situación no clasificada. */
    data class Unexpected(val message: String?) : AppError
}
