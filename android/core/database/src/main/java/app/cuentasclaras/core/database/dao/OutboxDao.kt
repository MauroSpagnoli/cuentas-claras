package app.cuentasclaras.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.cuentasclaras.core.database.entity.OutboxEntity
import kotlinx.coroutines.flow.Flow

/**
 * Cola de mutaciones pendientes de enviar al backend (outbox).
 *
 * El orden de drenado es el de creación para respetar las dependencias entre
 * operaciones (por ejemplo, crear antes de editar el mismo gasto). Los estados
 * van en el SQL como literales para evitar constantes interpoladas en las
 * consultas; deben coincidir con las de [OutboxEntity].
 */
@Dao
interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(operation: OutboxEntity)

    /** Operaciones pendientes de enviar, las más antiguas primero. */
    @Query("SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'PENDING'")
    suspend fun countPending(): Int

    /** Todas las filas (pendientes o fallidas) de un mismo recurso. */
    @Query("SELECT * FROM outbox WHERE entityId = :entityId ORDER BY createdAt ASC")
    suspend fun forEntity(entityId: String): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM outbox WHERE entityId = :entityId")
    suspend fun deleteForEntity(entityId: String)

    /** Registra un fallo transitorio sin sacar la operación de la cola. */
    @Query("UPDATE outbox SET attempts = :attempts, lastErrorCode = :code, lastError = :message WHERE id = :id")
    suspend fun markRetry(id: String, attempts: Int, code: String, message: String?)

    /** Marca un fallo definitivo que requiere intervención del usuario. */
    @Query(
        "UPDATE outbox SET attempts = :attempts, lastErrorCode = :code, lastError = :message, " +
            "status = 'FAILED' WHERE id = :id",
    )
    suspend fun markFailed(id: String, attempts: Int, code: String, message: String?)

    /**
     * Fallos definitivos de un grupo, para que la UI avise al usuario
     * (por ejemplo, un conflicto de versión al editar).
     */
    @Query("SELECT * FROM outbox WHERE groupId = :groupId AND status = 'FAILED' ORDER BY createdAt ASC")
    fun observeFailures(groupId: String): Flow<List<OutboxEntity>>

    /** Devuelve las operaciones fallidas a la cola para reintentarlas. */
    @Query("UPDATE outbox SET status = 'PENDING', lastErrorCode = NULL, lastError = NULL WHERE groupId = :groupId AND status = 'FAILED'")
    suspend fun resetFailed(groupId: String)
}
