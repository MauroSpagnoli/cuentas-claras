package app.cuentasclaras.domain.model

import app.cuentasclaras.domain.error.AppError

/**
 * Mutación que el outbox no pudo sincronizar de forma definitiva (no se
 * reintenta sola). La UI la muestra para que el usuario decida: por ejemplo, un
 * conflicto de versión al editar un gasto que otro cliente cambió antes.
 */
data class SyncFailure(
    val entityId: String,
    val operation: OutboxOperation,
    val error: AppError,
)
