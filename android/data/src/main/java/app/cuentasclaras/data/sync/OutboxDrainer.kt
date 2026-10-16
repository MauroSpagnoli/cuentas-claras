package app.cuentasclaras.data.sync

import androidx.room.withTransaction
import app.cuentasclaras.core.common.di.IoDispatcher
import app.cuentasclaras.core.database.CuentasClarasDatabase
import app.cuentasclaras.core.database.dao.OutboxDao
import app.cuentasclaras.core.database.entity.OutboxEntity
import app.cuentasclaras.core.network.api.ExpensesApi
import app.cuentasclaras.core.network.dto.CreateExpenseRequestDto
import app.cuentasclaras.core.network.dto.ExpenseDto
import app.cuentasclaras.core.network.dto.UpdateExpenseRequestDto
import app.cuentasclaras.core.network.safeApiCall
import app.cuentasclaras.core.network.safeUnitApiCall
import app.cuentasclaras.data.mapper.toEntity
import app.cuentasclaras.data.mapper.toShareEntities
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.result.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drena el outbox: envía al backend las mutaciones pendientes, en orden, y
 * actualiza la caché con la respuesta del servidor.
 *
 * Es idempotente por diseño: cada operación viaja con el `id` de su fila como
 * `Idempotency-Key`, así que reintentar tras un fallo no duplica el gasto.
 *
 * Los fallos transitorios (sin red, 5xx) dejan la operación en la cola y piden
 * un reintento con backoff; los definitivos (validación, conflicto, permisos) la
 * marcan como `FAILED` para que la UI avise al usuario.
 */
@Singleton
class OutboxDrainer @Inject constructor(
    private val api: ExpensesApi,
    private val database: CuentasClarasDatabase,
    private val outboxDao: OutboxDao,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    enum class Outcome {
        /** No queda nada por enviar (o solo fallos definitivos ya marcados). */
        COMPLETE,

        /** Hubo un fallo transitorio: conviene reintentar con backoff. */
        RETRY,
    }

    suspend fun drain(): Outcome = withContext(ioDispatcher) {
        while (true) {
            val batch = outboxDao.pending(BATCH_SIZE)
            if (batch.isEmpty()) break
            for (row in batch) {
                if (process(row) == Outcome.RETRY) return@withContext Outcome.RETRY
            }
        }
        Outcome.COMPLETE
    }

    private suspend fun process(row: OutboxEntity): Outcome = when (row.operation) {
        OutboxEntity.OPERATION_CREATE -> create(row)
        OutboxEntity.OPERATION_UPDATE -> update(row)
        OutboxEntity.OPERATION_DELETE -> delete(row)
        else -> {
            // Operación desconocida (versión futura): se descarta para no bloquear.
            outboxDao.delete(row.id)
            Outcome.COMPLETE
        }
    }

    private suspend fun create(row: OutboxEntity): Outcome {
        val body = decode<CreateExpenseRequestDto>(row) ?: return discard(row)
        return when (val result = safeApiCall(json) { api.create(row.groupId, body, row.id) }) {
            is AppResult.Success -> {
                applyCreated(row.entityId, result.value)
                outboxDao.delete(row.id)
                Outcome.COMPLETE
            }

            is AppResult.Failure -> failure(row, result.error)
        }
    }

    private suspend fun update(row: OutboxEntity): Outcome {
        val body = decode<UpdateExpenseRequestDto>(row) ?: return discard(row)
        return when (val result = safeApiCall(json) { api.update(row.entityId, body, row.id) }) {
            is AppResult.Success -> {
                cache(result.value)
                outboxDao.delete(row.id)
                Outcome.COMPLETE
            }

            is AppResult.Failure -> {
                // Si otro cliente editó antes, se trae su versión para no dejar
                // en la caché el cambio optimista que el servidor rechazó.
                if (result.error is AppError.Conflict) refreshFromServer(row.entityId)
                failure(row, result.error)
            }
        }
    }

    private suspend fun delete(row: OutboxEntity): Outcome =
        when (val result = safeUnitApiCall(json) { api.delete(row.entityId, row.id) }) {
            is AppResult.Success -> {
                outboxDao.delete(row.id)
                Outcome.COMPLETE
            }

            is AppResult.Failure -> failure(row, result.error)
        }

    /**
     * Aplica el resultado del alta: sustituye el gasto optimista (id local) por
     * el que devuelve el servidor, evitando duplicados tras un reintento.
     */
    private suspend fun applyCreated(placeholderId: String, created: ExpenseDto) {
        database.withTransaction {
            val expenseDao = database.expenseDao()
            expenseDao.clearShares(placeholderId)
            expenseDao.deleteExpense(placeholderId)
            cache(created)
        }
    }

    /** Guarda un gasto del servidor en la caché, sustituyendo sus partes previas. */
    private suspend fun cache(expense: ExpenseDto) {
        val expenseDao = database.expenseDao()
        expenseDao.upsert(expense.toEntity())
        expenseDao.clearShares(expense.id)
        val shares = expense.toShareEntities()
        if (shares.isNotEmpty()) expenseDao.upsertShares(shares)
    }

    /**
     * Ante un fallo, decide si se reintenta o se marca como definitivo. [attempts]
     * acota los reintentos transitorios para que un error permanente encubierto
     * no reintente sin fin.
     */
    private suspend fun failure(row: OutboxEntity, error: AppError): Outcome {
        val attempts = row.attempts + 1
        val code = SyncError.codeOf(error)
        val message = SyncError.messageOf(error)
        return if (SyncError.isTransient(error) && attempts < MAX_ATTEMPTS) {
            outboxDao.markRetry(row.id, attempts, code, message)
            Outcome.RETRY
        } else {
            outboxDao.markFailed(row.id, attempts, code, message)
            Outcome.COMPLETE
        }
    }

    /** Recarga del servidor el recurso afectado (por ejemplo, tras un 409). */
    private suspend fun refreshFromServer(entityId: String) {
        when (val result = safeApiCall(json) { api.get(entityId) }) {
            is AppResult.Success -> cache(result.value)
            is AppResult.Failure -> Unit
        }
    }

    /** Payload corrupto: no hay forma de reintentarlo, se descarta. */
    private suspend fun discard(row: OutboxEntity): Outcome {
        outboxDao.delete(row.id)
        return Outcome.COMPLETE
    }

    private inline fun <reified T> decode(row: OutboxEntity): T? =
        runCatching { json.decodeFromString<T>(row.payload) }.getOrNull()

    private companion object {
        const val BATCH_SIZE = 50

        /** Reintentos transitorios antes de rendirse y avisar al usuario. */
        const val MAX_ATTEMPTS = 5
    }
}
