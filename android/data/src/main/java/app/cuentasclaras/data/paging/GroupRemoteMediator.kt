package app.cuentasclaras.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import app.cuentasclaras.core.common.di.IoDispatcher
import app.cuentasclaras.core.database.CuentasClarasDatabase
import app.cuentasclaras.core.database.entity.GroupEntity
import app.cuentasclaras.core.database.entity.GroupSyncStateEntity
import app.cuentasclaras.core.network.api.GroupsApi
import app.cuentasclaras.core.network.safeApiCall
import app.cuentasclaras.data.mapper.toEntity
import app.cuentasclaras.domain.error.ApiException
import app.cuentasclaras.domain.result.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Sincroniza el listado de grupos: pide páginas al backend por cursor y las
 * escribe en Room. La UI sigue leyendo siempre de Room (fuente de verdad).
 *
 * El cursor opaco de la API se guarda en `group_sync_state`; no se usan
 * `RemoteKeys` por elemento porque la paginación del backend es solo hacia
 * adelante.
 */
@OptIn(ExperimentalPagingApi::class)
class GroupRemoteMediator(
    private val api: GroupsApi,
    private val database: CuentasClarasDatabase,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RemoteMediator<Int, GroupEntity>() {

    override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, GroupEntity>,
    ): MediatorResult {
        val cursor = when (loadType) {
            LoadType.REFRESH -> null
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val sync = withContext(ioDispatcher) { database.groupDao().syncState() }
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
                if (sync.endReached) {
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                sync.nextCursor ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        }

        return withContext(ioDispatcher) {
            when (val result = safeApiCall(json) { api.list(limit = PAGE_SIZE, cursor = cursor) }) {
                is AppResult.Failure -> MediatorResult.Error(ApiException(result.error))
                is AppResult.Success -> {
                    val page = result.value
                    database.withTransaction {
                        if (loadType == LoadType.REFRESH) {
                            database.groupDao().clearGroups()
                        }
                        database.groupDao().upsertAll(page.items.map { it.toEntity() })
                        database.groupDao().upsertSyncState(
                            GroupSyncStateEntity(
                                nextCursor = page.nextCursor,
                                endReached = !page.hasMore,
                            ),
                        )
                    }
                    MediatorResult.Success(endOfPaginationReached = !page.hasMore)
                }
            }
        }
    }

    companion object {
        /** Tamaño de página acordado con la API (máximo 20 por defecto del backend). */
        const val PAGE_SIZE = 20
    }
}
