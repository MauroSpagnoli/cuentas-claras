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
import app.cuentasclaras.core.database.entity.ExpenseEntity
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
import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.model.ExpenseDraft
import app.cuentasclaras.domain.repository.ExpenseRepository
import app.cuentasclaras.domain.result.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalPagingApi::class)
class ExpenseRepositoryImpl @Inject constructor(
    private val api: ExpensesApi,
    private val database: CuentasClarasDatabase,
    private val expenseDao: ExpenseDao,
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

            is AppResult.Failure -> result
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

            is AppResult.Failure -> result
        }
    }

    override suspend fun deleteExpense(expenseId: String): AppResult<Unit> = withContext(ioDispatcher) {
        when (val result = safeUnitApiCall(json) { api.delete(expenseId) }) {
            is AppResult.Success -> {
                database.withTransaction {
                    expenseDao.clearShares(expenseId)
                    expenseDao.deleteExpense(expenseId)
                }
                AppResult.Success(Unit)
            }

            is AppResult.Failure -> result
        }
    }

    /** Guarda el gasto y su reparto en la caché, sustituyendo las partes previas. */
    private suspend fun cache(expense: ExpenseDto) {
        database.withTransaction {
            expenseDao.upsert(expense.toEntity())
            expenseDao.clearShares(expense.id)
            val shares = expense.toShareEntities()
            if (shares.isNotEmpty()) expenseDao.upsertShares(shares)
        }
    }
}
