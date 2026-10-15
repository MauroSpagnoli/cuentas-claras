package app.cuentasclaras.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.RemoteMediator.MediatorResult
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cuentasclaras.core.database.CuentasClarasDatabase
import app.cuentasclaras.core.database.entity.GroupEntity
import app.cuentasclaras.core.database.entity.GroupSyncStateEntity
import app.cuentasclaras.core.network.api.GroupsApi
import app.cuentasclaras.core.network.dto.GroupDto
import app.cuentasclaras.core.network.dto.PageDto
import app.cuentasclaras.domain.error.ApiException
import app.cuentasclaras.domain.error.AppError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

@OptIn(ExperimentalPagingApi::class)
@RunWith(RobolectricTestRunner::class)
class GroupRemoteMediatorTest {

    private lateinit var database: CuentasClarasDatabase
    private val api: GroupsApi = mockk()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CuentasClarasDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `refresh stores the first page and the next cursor`() = runTest {
        everyListReturns(pageOf(group("g-1"), cursor = "cursor-1", hasMore = true))

        val result = mediator(UnconfinedTestDispatcher(testScheduler)).load(LoadType.REFRESH, pagingState())

        assertEquals(false, (result as MediatorResult.Success).endOfPaginationReached)
        assertEquals("cursor-1", database.groupDao().syncState()?.nextCursor)
        assertEquals(listOf("g-1"), cachedGroupIds())
    }

    @Test
    fun `refresh replaces whatever was cached before`() = runTest {
        database.groupDao().upsert(groupEntity("stale"))
        everyListReturns(pageOf(group("g-1"), cursor = null, hasMore = false))

        mediator(UnconfinedTestDispatcher(testScheduler)).load(LoadType.REFRESH, pagingState())

        assertEquals(listOf("g-1"), cachedGroupIds())
    }

    @Test
    fun `append uses the stored cursor and appends the page`() = runTest {
        database.groupDao().upsert(groupEntity("g-1"))
        database.groupDao().upsertSyncState(GroupSyncStateEntity(nextCursor = "cursor-1", endReached = false))
        everyListReturns(pageOf(group("g-2"), cursor = null, hasMore = false))

        val result = mediator(UnconfinedTestDispatcher(testScheduler)).load(LoadType.APPEND, pagingState())

        assertEquals(true, (result as MediatorResult.Success).endOfPaginationReached)
        coVerify(exactly = 1) { api.list(limit = 20, cursor = "cursor-1") }
        assertEquals(setOf("g-1", "g-2"), cachedGroupIds().toSet())
        assertEquals(true, database.groupDao().syncState()?.endReached)
    }

    @Test
    fun `append stops when the sync state marks the end`() = runTest {
        database.groupDao().upsertSyncState(GroupSyncStateEntity(nextCursor = null, endReached = true))

        val result = mediator(UnconfinedTestDispatcher(testScheduler)).load(LoadType.APPEND, pagingState())

        assertEquals(true, (result as MediatorResult.Success).endOfPaginationReached)
        coVerify(exactly = 0) { api.list(any(), any()) }
    }

    @Test
    fun `append without any sync state does nothing`() = runTest {
        val result = mediator(UnconfinedTestDispatcher(testScheduler)).load(LoadType.APPEND, pagingState())

        assertEquals(true, (result as MediatorResult.Success).endOfPaginationReached)
        coVerify(exactly = 0) { api.list(any(), any()) }
    }

    @Test
    fun `prepend is not supported`() = runTest {
        val result = mediator(UnconfinedTestDispatcher(testScheduler)).load(LoadType.PREPEND, pagingState())

        assertEquals(true, (result as MediatorResult.Success).endOfPaginationReached)
        coVerify(exactly = 0) { api.list(any(), any()) }
    }

    @Test
    fun `a failing page becomes a mediator error carrying the domain error`() = runTest {
        coEvery { api.list(any(), any()) } returns errorResponse()

        val result = mediator(UnconfinedTestDispatcher(testScheduler)).load(LoadType.REFRESH, pagingState())

        val error = (result as MediatorResult.Error).throwable
        assertTrue(error is ApiException)
        assertEquals(
            AppError.Conflict("Ya perteneces a este grupo"),
            (error as ApiException).error,
        )
        assertEquals(0, cachedGroupIds().size)
    }

    @Test
    fun `network failure is reported as a mediator error`() = runTest {
        coEvery { api.list(any(), any()) } throws java.io.IOException("sin red")

        val result = mediator(UnconfinedTestDispatcher(testScheduler)).load(LoadType.REFRESH, pagingState())

        val error = (result as MediatorResult.Error).throwable as ApiException
        assertEquals(AppError.Network, error.error)
    }

    private fun mediator(dispatcher: CoroutineDispatcher) =
        GroupRemoteMediator(api, database, json, dispatcher)

    private fun everyListReturns(page: PageDto<GroupDto>) {
        coEvery { api.list(any(), any()) } returns Response.success(page)
    }

    private fun errorResponse(code: Int = 409): Response<PageDto<GroupDto>> = Response.error(
        code,
        """{"title":"Conflict","status":$code,"detail":"Ya perteneces a este grupo","code":"ALREADY_MEMBER"}"""
            .toResponseBody("application/problem+json".toMediaType()),
    )

    private suspend fun cachedGroupIds(): List<String> {
        val params = PagingSource.LoadParams.Refresh<Int>(key = null, loadSize = 50, placeholdersEnabled = false)
        val page = database.groupDao().pagingSource().load(params) as PagingSource.LoadResult.Page
        return page.data.map { it.id }
    }

    private fun pagingState() = PagingState<Int, GroupEntity>(
        pages = emptyList(),
        anchorPosition = null,
        config = PagingConfig(pageSize = 20),
        leadingPlaceholderCount = 0,
    )

    private fun pageOf(vararg groups: GroupDto, cursor: String?, hasMore: Boolean) =
        PageDto(items = groups.toList(), nextCursor = cursor, hasMore = hasMore)

    private fun group(id: String) = GroupDto(
        id = id,
        name = "Grupo $id",
        currency = "EUR",
        memberCount = 1,
    )

    private fun groupEntity(id: String) = GroupEntity(
        id = id,
        name = "Grupo $id",
        description = null,
        currency = "EUR",
        inviteCode = null,
        memberCount = 1,
        createdAt = null,
        version = 0,
    )
}
