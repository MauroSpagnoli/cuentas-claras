package app.cuentasclaras.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import app.cuentasclaras.core.common.di.IoDispatcher
import app.cuentasclaras.core.database.CuentasClarasDatabase
import app.cuentasclaras.core.database.dao.ExpenseDao
import app.cuentasclaras.core.database.dao.OutboxDao
import app.cuentasclaras.core.database.entity.ExpenseEntity
import app.cuentasclaras.core.database.entity.ExpenseShareEntity
import app.cuentasclaras.core.database.entity.OutboxEntity
import app.cuentasclaras.core.network.api.ExpensesApi
import app.cuentasclaras.core.network.dto.ExpenseDto
import app.cuentasclaras.core.network.safeApiCall
import app.cuentasclaras.core.network.safeUnitApiCall
import app.cuentasclaras.data.mapper.toCreateRequest
import app.cuentasclaras.data.mapper.toDomain
import app.cuentasclaras.data.mapper.toEntity
import app.cuentasclaras.data.mapper.toShareEntities
import app.cuentasclaras.data.mapper.toUpdateRequest
import app.cuentasclaras.data.paging.ExpenseRemoteMediator
import app.cuentasclaras.data.sync.SyncScheduler
import app.cuentasclaras.data.sync.toSyncFailure
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.model.ExpenseDraft
import app.cuentasclaras.domain.model.ExpenseShare
import app.cuentasclaras.domain.model.OutboxOperation
import app.cuentasclaras.domain.model.SyncFailure
import app.cuentasclaras.domain.repository.ExpenseRepository
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.service.SplitCalculator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Contrato de gastos sobre Room como fuente de verdad.
 *
 * Las altas, ediciones y bajas son **offline-first**: con red se aplican en el
 * servidor y se cachea la respuesta; sin red se reflejan de forma optimista en
 * Room y se encolan en el outbox, que un `SyncWorker` drena después sin
 * duplicados (la fila del outbox actúa de `Idempotency-Key`).
 */
@Singleton
@OptIn(ExperimentalPagingApi::class)
class ExpenseRepositoryImpl @Inject constructor(
    private val api: ExpensesApi,
    private val database: CuentasClarasDatabase,
    private val expenseDao: ExpenseDao,
    private val outboxDao: OutboxDao,
    private val scheduler: SyncScheduler,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ExpenseRepository {

    override fun observeExpenses(groupId: String): Flow<PagingData<Expense>> = Pager(
        config = PagingConfig(
            pageSize = ExpenseRemoteMediator.PAGE_SIZE,
            initialLoadSize = ExpenseRemoteMediator.PAGE_SIZE,
            enablePlaceholders = false,
        ),
        remoteMediator = ExpenseRemoteMediator(groupId, api, database, json, ioDispatcher),
        pagingSourceFactory = { expenseDao.pagingSource(groupId) },
    ).flow.map { data -> data.map(ExpenseEntity::toDomain) }

    override fun observeExpense(expenseId: String): Flow<Expense?> = combine(
        expenseDao.observeById(expenseId),
        expenseDao.observeShares(expenseId),
    ) { expense, shares ->
        expense?.toDomain(shares.map { it.toDomain() })
    }

    override fun observeSyncFailures(groupId: String): Flow<List<SyncFailure>> =
        outboxDao.observeFailures(groupId).map { rows -> rows.mapNotNull { it.toSyncFailure() } }

    override suspend fun refreshExpense(expenseId: String): AppResult<Expense> = withContext(ioDispatcher) {
        when (val result = safeApiCall(json) { api.get(expenseId) }) {
            is AppResult.Success -> {
                cache(result.value)
                AppResult.Success(result.value.toDomain())
            }

            is AppResult.Failure -> result
        }
    }

    override suspend fun createExpense(
        groupId: String,
        draft: ExpenseDraft,
    ): AppResult<Expense> = withContext(ioDispatcher) {
        when (val result = safeApiCall(json) { api.create(groupId, draft.toCreateRequest()) }) {
            is AppResult.Success -> {
                cache(result.value)
                AppResult.Success(result.value.toDomain())
            }

            is AppResult.Failure ->
                if (result.error == AppError.Network) offlineCreate(groupId, draft) else result
        }
    }

    override suspend fun updateExpense(
        expenseId: String,
        draft: ExpenseDraft,
    ): AppResult<Expense> = withContext(ioDispatcher) {
        when (val result = safeApiCall(json) { api.update(expenseId, draft.toUpdateRequest()) }) {
            is AppResult.Success -> {
                cache(result.value)
                AppResult.Success(result.value.toDomain())
            }

            is AppResult.Failure ->
                if (result.error == AppError.Network) offlineUpdate(expenseId, draft) else result
        }
    }

    override suspend fun deleteExpense(expenseId: String): AppResult<Unit> = withContext(ioDispatcher) {
        when (val result = safeUnitApiCall(json) { api.delete(expenseId) }) {
            is AppResult.Success -> {
                removeFromCache(expenseId)
                AppResult.Success(Unit)
            }

            is AppResult.Failure ->
                if (result.error == AppError.Network) offlineDelete(expenseId) else result
        }
    }

    override suspend fun retrySync(groupId: String): Unit = withContext(ioDispatcher) {
        outboxDao.resetFailed(groupId)
        scheduler.schedule()
    }

    // --- Escritura optimista (sin red) -------------------------------------------------

    private suspend fun offlineCreate(groupId: String, draft: ExpenseDraft): AppResult<Expense> {
        val localId = UUID.randomUUID().toString()
        val shares = SplitCalculator.resolve(draft.amountMinor, draft.splitType, draft.shares)
        val currency = database.groupDao().getById(groupId)?.currency ?: DEFAULT_CURRENCY
        val createdBy = database.userDao().current()?.id.orEmpty()

        val entity = ExpenseEntity(
            id = localId,
            groupId = groupId,
            description = draft.description,
            amountMinor = draft.amountMinor,
            currency = currency,
            paidByUserId = draft.paidByUserId,
            splitType = draft.splitType.name,
            category = draft.category,
            expenseDate = draft.expenseDate,
            notes = draft.notes,
            createdBy = createdBy,
            createdAt = null,
            version = 0,
            pendingOperation = OutboxEntity.OPERATION_CREATE,
        )

        database.withTransaction {
            expenseDao.upsert(entity)
            expenseDao.clearShares(localId)
            val shareEntities = shares.toShareEntities(localId)
            if (shareEntities.isNotEmpty()) expenseDao.upsertShares(shareEntities)

            outboxDao.enqueue(
                outboxOperation(
                    entityId = localId,
                    groupId = groupId,
                    operation = OutboxEntity.OPERATION_CREATE,
                    payload = json.encodeToString(draft.toCreateRequest()),
                ),
            )
        }
        scheduler.schedule()

        return AppResult.Success(entity.toDomain(shares.toDomainShares()))
    }

    private suspend fun offlineUpdate(expenseId: String, draft: ExpenseDraft): AppResult<Expense> {
        val existing = expenseDao.getById(expenseId) ?: return AppResult.Failure(AppError.NotFound)
        val shares = SplitCalculator.resolve(draft.amountMinor, draft.splitType, draft.shares)

        val operation = database.withTransaction {
            // Un alta aún sin confirmar sigue siendo un alta: se reescribe su
            // cuerpo en lugar de encolar un PATCH sobre un id que el servidor
            // todavía no conoce.
            val createRow = outboxDao.forEntity(expenseId)
                .firstOrNull { it.operation == OutboxEntity.OPERATION_CREATE }
            val op = if (createRow != null) {
                OutboxEntity.OPERATION_CREATE
            } else {
                OutboxEntity.OPERATION_UPDATE
            }

            expenseDao.upsert(
                existing.copy(
                    description = draft.description,
                    amountMinor = draft.amountMinor,
                    paidByUserId = draft.paidByUserId,
                    splitType = draft.splitType.name,
                    category = draft.category,
                    expenseDate = draft.expenseDate,
                    notes = draft.notes,
                    pendingOperation = op,
                ),
            )
            expenseDao.clearShares(expenseId)
            val shareEntities = shares.toShareEntities(expenseId)
            if (shareEntities.isNotEmpty()) expenseDao.upsertShares(shareEntities)

            val payload = if (op == OutboxEntity.OPERATION_CREATE) {
                json.encodeToString(draft.toCreateRequest())
            } else {
                json.encodeToString(draft.toUpdateRequest().copy(version = existing.version))
            }
            outboxDao.deleteForEntity(expenseId)
            outboxDao.enqueue(
                outboxOperation(
                    id = createRow?.id,
                    entityId = expenseId,
                    groupId = existing.groupId,
                    operation = op,
                    payload = payload,
                ),
            )
            op
        }
        scheduler.schedule()

        val updated = existing.copy(
            description = draft.description,
            amountMinor = draft.amountMinor,
            paidByUserId = draft.paidByUserId,
            splitType = draft.splitType.name,
            category = draft.category,
            expenseDate = draft.expenseDate,
            notes = draft.notes,
            pendingOperation = operation,
        )
        return AppResult.Success(updated.toDomain(shares.toDomainShares()))
    }

    private suspend fun offlineDelete(expenseId: String): AppResult<Unit> {
        val existing = expenseDao.getById(expenseId) ?: return AppResult.Success(Unit)

        database.withTransaction {
            // Si el gasto nunca llegó al servidor (alta pendiente), basta con
            // descartar la operación encolada y borrarlo de la caché.
            val createRow = outboxDao.forEntity(expenseId)
                .firstOrNull { it.operation == OutboxEntity.OPERATION_CREATE }
            outboxDao.deleteForEntity(expenseId)

            expenseDao.clearShares(expenseId)
            expenseDao.deleteExpense(expenseId)

            if (createRow == null) {
                outboxDao.enqueue(
                    outboxOperation(
                        entityId = expenseId,
                        groupId = existing.groupId,
                        operation = OutboxEntity.OPERATION_DELETE,
                        payload = "",
                    ),
                )
            }
        }
        scheduler.schedule()

        return AppResult.Success(Unit)
    }

    // --- Caché local -------------------------------------------------------------------

    /** Guarda el gasto y su reparto en la caché, sustituyendo las partes previas. */
    private suspend fun cache(expense: ExpenseDto) {
        database.withTransaction {
            expenseDao.upsert(expense.toEntity())
            expenseDao.clearShares(expense.id)
            val shares = expense.toShareEntities()
            if (shares.isNotEmpty()) expenseDao.upsertShares(shares)
        }
    }

    private suspend fun removeFromCache(expenseId: String) {
        database.withTransaction {
            expenseDao.clearShares(expenseId)
            expenseDao.deleteExpense(expenseId)
        }
    }

    private fun outboxOperation(
        entityId: String,
        groupId: String,
        operation: String,
        payload: String,
        id: String? = null,
    ): OutboxEntity = OutboxEntity(
        id = id ?: UUID.randomUUID().toString(),
        entityType = OutboxEntity.ENTITY_EXPENSE,
        entityId = entityId,
        groupId = groupId,
        operation = operation,
        payload = payload,
        status = OutboxEntity.STATUS_PENDING,
        createdAt = System.currentTimeMillis(),
    )

    private fun List<SplitCalculator.ResolvedShare>.toShareEntities(
        expenseId: String,
    ): List<ExpenseShareEntity> = map { share ->
        ExpenseShareEntity(
            expenseId = expenseId,
            userId = share.userId,
            amountMinor = share.amountMinor,
            percentage = share.percentage?.toPlainString(),
            weight = share.weight?.toPlainString(),
        )
    }

    private fun List<SplitCalculator.ResolvedShare>.toDomainShares(): List<ExpenseShare> = map { share ->
        ExpenseShare(
            userId = share.userId,
            amountMinor = share.amountMinor,
            percentage = share.percentage,
            weight = share.weight,
        )
    }

    private companion object {
        /** Moneda de reserva si el grupo aún no está en la caché. */
        const val DEFAULT_CURRENCY = "EUR"
    }
}
