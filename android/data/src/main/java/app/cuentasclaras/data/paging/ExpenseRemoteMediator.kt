package app.cuentasclaras.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import app.cuentasclaras.core.common.di.IoDispatcher
import app.cuentasclaras.core.database.CuentasClarasDatabase
import app.cuentasclaras.core.database.entity.ExpenseEntity
import app.cuentasclaras.core.database.entity.ExpenseSyncStateEntity
import app.cuentasclaras.core.network.api.ExpensesApi
import app.cuentasclaras.core.network.safeApiCall
import app.cuentasclaras.data.mapper.toEntity
import app.cuentasclaras.data.mapper.toShareEntities
import app.cuentasclaras.domain.error.ApiException
import app.cuentasclaras.domain.result.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Sincroniza el listado de gastos de un grupo: pide páginas al backend por
 * cursor y las escribe en Room. La UI sigue leyendo siempre de Room (fuente de
 * verdad).
 *
 * El cursor opaco se guarda en `expense_sync_state`, una fila por grupo, porque
 * se pueden cachear los gastos de varios grupos a la vez.
 */
@OptIn(ExperimentalPagingApi::class)
class ExpenseRemoteMediator(
    private val groupId: String,
    private val api: ExpensesApi,
    private val database: CuentasClarasDatabase,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RemoteMediator<Int, ExpenseEntity>() {

    override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ExpenseEntity>,
    ): MediatorResult {
        val cursor = when (loadType) {
            LoadType.REFRESH -> null
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val sync = withContext(ioDispatcher) { database.expenseDao().syncState(groupId) }
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
                if (sync.endReached) return MediatorResult.Success(endOfPaginationReached = true)
                sync.nextCursor ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        }

        return withContext(ioDispatcher) {
            when (val result = safeApiCall(json) { api.list(groupId, PAGE_SIZE, cursor) }) {
                is AppResult.Success -> {
                    val page = result.value
                    database.withTransaction {
                        val dao = database.expenseDao()
                        if (loadType == LoadType.REFRESH) {
                            dao.clearSharesOfGroup(groupId)
                            dao.clearExpenses(groupId)
                        }
                        dao.upsertAll(page.items.map { it.toEntity() })
                        val shares = page.items.flatMap { it.toShareEntities() }
                        if (shares.isNotEmpty()) dao.upsertShares(shares)
                        dao.upsertSyncState(
                            ExpenseSyncStateEntity(
                                groupId = groupId,
                                nextCursor = page.nextCursor,
                                endReached = !page.hasMore,
                            ),
                        )
                    }
                    MediatorResult.Success(endOfPaginationReached = !page.hasMore)
                }

                is AppResult.Failure -> MediatorResult.Error(ApiException(result.error))
            }
        }
    }

    companion object {
        /** Tamaño de página acordado con la API (máximo 20 por defecto del backend). */
        const val PAGE_SIZE = 20
    }
}
