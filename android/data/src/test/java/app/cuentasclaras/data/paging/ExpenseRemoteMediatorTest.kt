package app.cuentasclaras.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator.MediatorResult
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cuentasclaras.core.database.CuentasClarasDatabase
import app.cuentasclaras.core.database.entity.ExpenseEntity
import app.cuentasclaras.core.database.entity.ExpenseShareEntity
import app.cuentasclaras.core.database.entity.ExpenseSyncStateEntity
import app.cuentasclaras.core.network.api.ExpensesApi
import app.cuentasclaras.core.network.dto.ExpenseDto
import app.cuentasclaras.core.network.dto.ExpenseShareDto
import app.cuentasclaras.core.network.dto.PageDto
import app.cuentasclaras.domain.error.ApiException
import app.cuentasclaras.domain.error.AppError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.math.BigDecimal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
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
class ExpenseRemoteMediatorTest {

    private lateinit var database: CuentasClarasDatabase
    private val api: ExpensesApi = mockk()

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
    fun `refresh stores the first page with its shares and cursor`() = runTest {
        everyListReturns(pageOf(expense("e-1"), cursor = "cursor-1", hasMore = true))

        val result = mediator("g-1", UnconfinedTestDispatcher(testScheduler))
            .load(LoadType.REFRESH, pagingState())

        assertEquals(false, (result as MediatorResult.Success).endOfPaginationReached)
        assertEquals("cursor-1", database.expenseDao().syncState("g-1")?.nextCursor)
        assertEquals(listOf("e-1"), cachedIds("g-1"))
        assertEquals(2, database.expenseDao().observeShares("e-1").first().size)
    }

    @Test
    fun `refresh replaces the cached page of that group`() = runTest {
        database.expenseDao().upsert(
            expenseEntity("stale", "g-1").copy(expenseDate = "2026-01-01"),
        )
        everyListReturns(pageOf(expense("e-1"), cursor = null, hasMore = false))

        mediator("g-1", UnconfinedTestDispatcher(testScheduler)).load(LoadType.REFRESH, pagingState())

        assertEquals(listOf("e-1"), cachedIds("g-1"))
    }

    @Test
    fun `refresh only clears the expenses of its own group`() = runTest {
        database.expenseDao().upsert(expenseEntity("other", groupId = "g-2"))
        everyListReturns(pageOf(expense("e-1"), cursor = null, hasMore = false))

        mediator("g-1", UnconfinedTestDispatcher(testScheduler)).load(LoadType.REFRESH, pagingState())

        assertEquals(listOf("e-1"), cachedIds("g-1"))
        assertEquals(listOf("other"), cachedIds("g-2"))
    }

    @Test
    fun `refresh preserves the expenses that are still pending to sync`() = runTest {
        database.expenseDao().upsert(
            expenseEntity("local-1", groupId = "g-1").copy(pendingOperation = "CREATE"),
        )
        database.expenseDao().upsertShares(
            listOf(ExpenseShareEntity("local-1", "u-1", 3000, null, null)),
        )
        everyListReturns(pageOf(expense("e-1"), cursor = null, hasMore = false))

        mediator("g-1", UnconfinedTestDispatcher(testScheduler)).load(LoadType.REFRESH, pagingState())

        assertEquals(setOf("e-1", "local-1"), cachedIds("g-1").toSet())
        assertEquals(1, database.expenseDao().observeShares("local-1").first().size)
    }

    @Test
    fun `refresh does not overwrite a pending edit with the server version`() = runTest {
        database.expenseDao().upsert(
            expenseEntity("e-1", groupId = "g-1").copy(
                description = "Editado sin red",
                pendingOperation = "UPDATE",
            ),
        )
        everyListReturns(pageOf(expense("e-1"), cursor = null, hasMore = false))

        mediator("g-1", UnconfinedTestDispatcher(testScheduler)).load(LoadType.REFRESH, pagingState())

        val cached = database.expenseDao().observeById("e-1").first()
        assertEquals("Editado sin red", cached?.description)
        assertEquals("UPDATE", cached?.pendingOperation)
    }

    @Test
    fun `append uses the stored cursor and appends the page`() = runTest {
        database.expenseDao().upsert(expenseEntity("e-1"))
        database.expenseDao().upsertSyncState(ExpenseSyncStateEntity("g-1", "cursor-1", false))
        everyListReturns(pageOf(expense("e-2"), cursor = null, hasMore = false))

        val result = mediator("g-1", UnconfinedTestDispatcher(testScheduler))
            .load(LoadType.APPEND, pagingState())

        assertEquals(true, (result as MediatorResult.Success).endOfPaginationReached)
        coVerify(exactly = 1) { api.list("g-1", 20, "cursor-1") }
        assertEquals(setOf("e-1", "e-2"), cachedIds("g-1").toSet())
        assertEquals(true, database.expenseDao().syncState("g-1")?.endReached)
    }

    @Test
    fun `append reads the cursor of its own group`() = runTest {
        database.expenseDao().upsertSyncState(ExpenseSyncStateEntity("g-1", "cursor-1", false))
        database.expenseDao().upsertSyncState(ExpenseSyncStateEntity("g-2", "cursor-2", false))
        everyListReturns(pageOf(expense("e-9", groupId = "g-1"), cursor = null, hasMore = false))

        mediator("g-1", UnconfinedTestDispatcher(testScheduler)).load(LoadType.APPEND, pagingState())

        coVerify(exactly = 1) { api.list("g-1", 20, "cursor-1") }
        coVerify(exactly = 0) { api.list("g-2", any(), any()) }
    }

    @Test
    fun `append stops when the sync state marks the end`() = runTest {
        database.expenseDao().upsertSyncState(ExpenseSyncStateEntity("g-1", null, true))

        val result = mediator("g-1", UnconfinedTestDispatcher(testScheduler))
            .load(LoadType.APPEND, pagingState())

        assertEquals(true, (result as MediatorResult.Success).endOfPaginationReached)
        coVerify(exactly = 0) { api.list(any(), any(), any()) }
    }

    @Test
    fun `append without any sync state does nothing`() = runTest {
        val result = mediator("g-1", UnconfinedTestDispatcher(testScheduler))
            .load(LoadType.APPEND, pagingState())

        assertEquals(true, (result as MediatorResult.Success).endOfPaginationReached)
        coVerify(exactly = 0) { api.list(any(), any(), any()) }
    }

    @Test
    fun `prepend is not supported`() = runTest {
        val result = mediator("g-1", UnconfinedTestDispatcher(testScheduler))
            .load(LoadType.PREPEND, pagingState())

        assertEquals(true, (result as MediatorResult.Success).endOfPaginationReached)
        coVerify(exactly = 0) { api.list(any(), any(), any()) }
    }

    @Test
    fun `a failing page becomes a mediator error carrying the domain error`() = runTest {
        coEvery { api.list(any(), any(), any()) } returns errorResponse()

        val result = mediator("g-1", UnconfinedTestDispatcher(testScheduler))
            .load(LoadType.REFRESH, pagingState())

        val error = (result as MediatorResult.Error).throwable
        assertTrue(error is ApiException)
        assertEquals(AppError.Conflict("Version desactualizada"), (error as ApiException).error)
        assertEquals(0, cachedIds("g-1").size)
    }

    @Test
    fun `network failure is reported as a mediator error`() = runTest {
        coEvery { api.list(any(), any(), any()) } throws IOException("sin red")

        val result = mediator("g-1", UnconfinedTestDispatcher(testScheduler))
            .load(LoadType.REFRESH, pagingState())

        val error = (result as MediatorResult.Error).throwable as ApiException
        assertEquals(AppError.Network, error.error)
    }

    private fun mediator(groupId: String, dispatcher: CoroutineDispatcher) =
        ExpenseRemoteMediator(groupId, api, database, json, dispatcher)

    private fun everyListReturns(page: PageDto<ExpenseDto>) {
        coEvery { api.list(any(), any(), any()) } returns Response.success(page)
    }

    private fun errorResponse(code: Int = 409): Response<PageDto<ExpenseDto>> = Response.error(
        code,
        """{"title":"Conflict","status":$code,"detail":"Version desactualizada","code":"STALE_VERSION"}"""
            .toResponseBody("application/problem+json".toMediaType()),
    )

    private suspend fun cachedIds(groupId: String): List<String> {
        val params = PagingSource.LoadParams.Refresh<Int>(key = null, loadSize = 50, placeholdersEnabled = false)
        val page = database.expenseDao().pagingSource(groupId).load(params) as PagingSource.LoadResult.Page
        return page.data.map { it.id }
    }

    private fun pagingState() = PagingState<Int, ExpenseEntity>(
        pages = emptyList(),
        anchorPosition = null,
        config = PagingConfig(pageSize = 20),
        leadingPlaceholderCount = 0,
    )

    private fun pageOf(vararg expenses: ExpenseDto, cursor: String?, hasMore: Boolean) =
        PageDto(items = expenses.toList(), nextCursor = cursor, hasMore = hasMore)

    private fun expense(id: String, groupId: String = "g-1") = ExpenseDto(
        id = id,
        groupId = groupId,
        description = "Gasto $id",
        amountMinor = 3000,
        currency = "EUR",
        paidByUserId = "u-1",
        splitType = "PERCENTAGE",
        expenseDate = "2026-02-01",
        createdBy = "u-1",
        version = 1,
        shares = listOf(
            ExpenseShareDto("u-1", 1000, percentage = BigDecimal("33.33")),
            ExpenseShareDto("u-2", 2000, percentage = BigDecimal("66.67")),
        ),
    )

    private fun expenseEntity(id: String, groupId: String = "g-1") = ExpenseEntity(
        id = id,
        groupId = groupId,
        description = "Gasto $id",
        amountMinor = 3000,
        currency = "EUR",
        paidByUserId = "u-1",
        splitType = "EQUAL",
        category = null,
        expenseDate = "2026-02-01",
        notes = null,
        createdBy = "u-1",
        createdAt = null,
        version = 0,
    )
}
