package app.cuentasclaras.domain.error

/**
 * Envuelve un [AppError] para poder propagarlo a través de APIs que solo
 * admiten `Throwable` (por ejemplo el `LoadState.Error` de Paging). La capa de
 * presentación lo desenvuelve para volver a obtener el error de dominio.
 */
class ApiException(val error: AppError) : Exception(error.toString())
