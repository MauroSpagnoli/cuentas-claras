package app.cuentasclaras.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import app.cuentasclaras.core.common.di.IoDispatcher
import app.cuentasclaras.core.network.api.BalancesApi
import app.cuentasclaras.core.network.safeApiCall
import app.cuentasclaras.data.mapper.toDomain
import app.cuentasclaras.data.mapper.toRequest
import app.cuentasclaras.data.paging.SettlementPagingSource
import app.cuentasclaras.domain.model.GroupBalances
import app.cuentasclaras.domain.model.Settlement
import app.cuentasclaras.domain.model.SettlementDraft
import app.cuentasclaras.domain.model.SettlementPlan
import app.cuentasclaras.domain.repository.BalanceRepository
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.result.map
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación online de balances y pagos: el backend es la fuente de verdad y
 * no se cachea nada, porque los balances son cálculos derivados que cambian con
 * cada gasto o pago.
 */
@Singleton
class BalanceRepositoryImpl @Inject constructor(
    private val api: BalancesApi,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BalanceRepository {

    override suspend fun loadBalances(groupId: String): AppResult<GroupBalances> =
        withContext(ioDispatcher) {
            safeApiCall(json) { api.balances(groupId) }.map { it.toDomain() }
        }

    override suspend fun loadSettlementPlan(groupId: String): AppResult<SettlementPlan> =
        withContext(ioDispatcher) {
            safeApiCall(json) { api.settlementPlan(groupId) }.map { it.toDomain() }
        }

    override fun observeSettlements(groupId: String): Flow<PagingData<Settlement>> = Pager(
        config = PagingConfig(
            pageSize = SettlementPagingSource.PAGE_SIZE,
            initialLoadSize = SettlementPagingSource.PAGE_SIZE,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = { SettlementPagingSource(groupId, api, json, ioDispatcher) },
    ).flow

    override suspend fun registerSettlement(
        groupId: String,
        draft: SettlementDraft,
    ): AppResult<Settlement> = withContext(ioDispatcher) {
        safeApiCall(json) { api.registerSettlement(groupId, draft.toRequest()) }
            .map { it.toDomain() }
    }
}
