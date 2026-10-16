package app.cuentasclaras.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.cuentasclaras.core.common.di.IoDispatcher
import app.cuentasclaras.core.network.api.BalancesApi
import app.cuentasclaras.core.network.safeApiCall
import app.cuentasclaras.data.mapper.toDomain
import app.cuentasclaras.domain.error.ApiException
import app.cuentasclaras.domain.model.Settlement
import app.cuentasclaras.domain.result.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Página de pagos servida directamente por la red, sin caché: los balances y sus
 * pagos son cálculos derivados y el backend es la fuente de verdad.
 *
 * El cursor es opaco, así que `getRefreshKey` no puede reconstruir una posición
 * intermedia: cualquier recarga vuelve a empezar por la primera página.
 */
class SettlementPagingSource(
    private val groupId: String,
    private val api: BalancesApi,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PagingSource<String, Settlement>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Settlement> =
        withContext(ioDispatcher) {
            when (val result = safeApiCall(json) { api.settlements(groupId, PAGE_SIZE, params.key) }) {
                is AppResult.Success -> {
                    val page = result.value
                    LoadResult.Page(
                        data = page.items.map { it.toDomain() },
                        prevKey = null,
                        nextKey = page.nextCursor?.takeIf { page.hasMore },
                    )
                }

                is AppResult.Failure -> LoadResult.Error(ApiException(result.error))
            }
        }

    override fun getRefreshKey(state: PagingState<String, Settlement>): String? = null

    companion object {
        /** Tamaño de página acordado con la API (máximo 20 por defecto del backend). */
        const val PAGE_SIZE = 20
    }
}
