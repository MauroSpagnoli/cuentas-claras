package app.cuentasclaras.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mutación pendiente de enviar al backend (patrón *outbox*).
 *
 * Cada fila es un cambio que ya se reflejó de forma optimista en la caché y que
 * un `SyncWorker` drena cuando hay red. El `id` es un UUID que además viaja como
 * `Idempotency-Key`, de modo que un reintento no duplique la escritura.
 *
 * `payload` guarda el cuerpo JSON de la petición ya serializado, para que el
 * worker no tenga que reconstruirlo desde la caché (que puede haber cambiado).
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["status", "createdAt"]),
        Index(value = ["entityId"]),
    ],
)
data class OutboxEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val groupId: String,
    val operation: String,
    val payload: String,
    val status: String,
    val attempts: Int = 0,
    val lastErrorCode: String? = null,
    val lastError: String? = null,
    val createdAt: Long,
) {
    companion object {
        const val ENTITY_EXPENSE = "EXPENSE"

        const val OPERATION_CREATE = "CREATE"
        const val OPERATION_UPDATE = "UPDATE"
        const val OPERATION_DELETE = "DELETE"

        /** Pendiente de enviar (incluye los reintentos en curso). */
        const val STATUS_PENDING = "PENDING"

        /** Fallo definitivo: requiere intervención del usuario. */
        const val STATUS_FAILED = "FAILED"
    }
}
