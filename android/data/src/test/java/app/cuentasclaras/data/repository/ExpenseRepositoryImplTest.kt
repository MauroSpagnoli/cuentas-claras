package app.cuentasclaras.data.repository

import androidx.paging.PagingSource
import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cuentasclaras.core.database.CuentasClarasDatabase
import app.cuentasclaras.core.database.entity.ExpenseEntity
import app.cuentasclaras.core.database.entity.ExpenseShareEntity
import app.cuentasclaras.core.network.api.ExpensesApi
import app.cuentasclaras.core.network.dto.CreateExpenseRequestDto
import app.cuentasclaras.core.network.dto.ExpenseDto
import app.cuentasclaras.core.network.dto.ExpenseShareDto
import app.cuentasclaras.core.network.dto.PageDto
import app.cuentasclaras.core.network.dto.UpdateExpenseRequestDto
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.ExpenseDraft
import app.cuentasclaras.domain.model.ExpenseShareInput
import app.cuentasclaras.domain.model.SplitType
import app.cuentasclaras.domain.result.AppResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
class ExpenseRepositoryImplTest {

    private lateinit var database: CuentasClarasDatabase
    private val api: ExpensesApi = mockk()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private lateinit var repository: ExpenseRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CuentasClarasDatabase::class.java,
        ).build()
        repository = ExpenseRepositoryImpl(
            api,
            database,
            database.expenseDao(),
            json,
            Dispatchers.IO,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `createExpense caches the expense and its shares`() = runTest {
        coEvery { api.create("g-1", any()) } returns Response.success(
            expenseDto("e-1", shares = listOf(shareDto("u-1", 2125), shareDto("u-2", 2125))),
        )

        val result = repository.createExpense("g-1", draft())

        val expense = (result as AppResult.Success).value
        assertEquals("Cena", expense.description)
        assertEquals(SplitType.PERCENTAGE, expense.splitType)
        assertEquals(2, expense.shares.size)

        val cached = database.expenseDao().observeById("e-1").first()
        assertEquals("Cena", cached?.description)
        assertEquals(listOf("u-1", "u-2"), database.expenseDao().observeShares("e-1").first().map { it.userId })
    }

    @Test
    fun `createExpense without shares caches only the expense`() = runTest {
        coEvery { api.create("g-1", any()) } returns Response.success(expenseDto("e-1"))

        repository.createExpense("g-1", draft())

        assertEquals(0, database.expenseDao().observeShares("e-1").first().size)
    }

    @Test
    fun `createExpense maps a validation error`() = runTest {
        coEvery { api.create("g-1", any()) } returns problemResponse(400, "Bad Request", "importe invalido")

        val result = repository.createExpense("g-1", draft())

        assertEquals(AppError.Validation("importe invalido"), (result as AppResult.Failure).error)
        assertEquals(0, cachedIds("g-1").size)
    }

    @Test
    fun `createExpense sends the split parameters of the draft`() = runTest {
        val body = slot<CreateExpenseRequestDto>()
        coEvery { api.create("g-1", capture(body)) } returns Response.success(expenseDto("e-1"))

        repository.createExpense(
            "g-1",
            draft(
                splitType = SplitType.SHARES,
                shares = listOf(
                    ExpenseShareInput("u-1", weight = BigDecimal("1")),
                    ExpenseShareInput("u-2", weight = BigDecimal("2")),
                ),
            ),
        )

        assertEquals("SHARES", body.captured.splitType)
        assertEquals(BigDecimal("2"), body.captured.shares[1].weight)
    }

    @Test
    fun `updateExpense replaces the cached shares`() = runTest {
        cacheExpense(expenseDto("e-1", version = 1, shares = listOf(shareDto("u-1", 1000), shareDto("u-2", 3250))))
        coEvery { api.update("e-1", any()) } returns Response.success(
            expenseDto("e-1", version = 2, shares = listOf(shareDto("u-3", 4250))),
        )

        val result = repository.updateExpense("e-1", draft(version = 1))

        assertEquals(2L, (result as AppResult.Success).value.version)
        assertEquals(listOf("u-3"), database.expenseDao().observeShares("e-1").first().map { it.userId })
    }

    @Test
    fun `updateExpense sends the version and the empty optional fields`() = runTest {
        val body = slot<UpdateExpenseRequestDto>()
        coEvery { api.update("e-1", capture(body)) } returns Response.success(expenseDto("e-1", version = 2))

        repository.updateExpense("e-1", draft(version = 7, category = "", notes = ""))

        assertEquals(7L, body.captured.version)
        assertEquals("", body.captured.category)
        assertEquals("", body.captured.notes)
    }

    @Test
    fun `updateExpense maps a stale version conflict`() = runTest {
        coEvery { api.update("e-1", any()) } returns problemResponse(409, "Conflict", "Version desactualizada")

        val result = repository.updateExpense("e-1", draft(version = 1))

        assertEquals(AppError.Conflict("Version desactualizada"), (result as AppResult.Failure).error)
    }

    @Test
    fun `refreshExpense updates the cache`() = runTest {
        coEvery { api.get("e-1") } returns Response.success(expenseDto("e-1", description = "Cena larga", version = 3))

        val result = repository.refreshExpense("e-1")

        assertEquals("Cena larga", (result as AppResult.Success).value.description)
        assertEquals(3L, database.expenseDao().observeById("e-1").first()?.version)
    }

    @Test
    fun `refreshExpense keeps the previous cache on failure`() = runTest {
        cacheExpense(expenseDto("e-1", description = "Cena"))
        coEvery { api.get("e-1") } returns problemResponse(404, "Not Found", "el gasto no existe")

        val result = repository.refreshExpense("e-1")

        assertEquals(AppError.NotFound, (result as AppResult.Failure).error)
        assertEquals("Cena", repository.observeExpense("e-1").first()?.description)
    }

    @Test
    fun `deleteExpense drops the expense and its shares from the cache`() = runTest {
        cacheExpense(expenseDto("e-1", shares = listOf(shareDto("u-1", 4250))))
        coEvery { api.delete("e-1") } returns Response.success(Unit)

        val result = repository.deleteExpense("e-1")

        assertTrue(result is AppResult.Success)
        assertNull(database.expenseDao().observeById("e-1").first())
        assertEquals(0, database.expenseDao().observeShares("e-1").first().size)
        assertEquals(0, cachedIds("g-1").size)
    }

    @Test
    fun `a failing delete keeps the cached expense`() = runTest {
        cacheExpense(expenseDto("e-1"))
        coEvery { api.delete("e-1") } returns problemResponse(403, "Forbidden", "no eres el creador")

        val result = repository.deleteExpense("e-1")

        assertEquals(AppError.Forbidden, (result as AppResult.Failure).error)
        assertEquals("Cena", database.expenseDao().observeById("e-1").first()?.description)
    }

    @Test
    fun `observeExpense emits null when nothing is cached`() = runTest {
        coEvery { api.get(any()) } returns Response.success(expenseDto("e-2"))
        assertNull(repository.observeExpense("missing").first())
    }

    @Test
    fun `observeExpenses serves the cache refreshed from the network`() = runTest {
        coEvery { api.list(any(), any(), any()) } returns Response.success(
            PageDto(
                items = listOf(expenseDto("e-1", description = "Cena"), expenseDto("e-2", description = "Taxi")),
                nextCursor = null,
                hasMore = false,
            ),
        )

        val snapshot = withContext(Dispatchers.IO) { repository.observeExpenses("g-1").asSnapshot() }

        assertEquals(setOf("Cena", "Taxi"), snapshot.map { it.description }.toSet())
    }

    @Test
    fun `a failing refresh does not wipe the cached expenses`() = runTest {
        database.expenseDao().upsert(expenseEntity("e-cached", description = "Cacheado"))
        coEvery { api.list(any(), any(), any()) } returns problemResponse(500, "Boom", "servidor caido")

        val snapshot = runCatching {
            withContext(Dispatchers.IO) { repository.observeExpenses("g-1").asSnapshot() }
        }

        // Paging propaga el fallo del refresco remoto...
        assertTrue(snapshot.isFailure)
        // ...pero Room sigue siendo la fuente de verdad y conserva lo cacheado.
        assertEquals("Cacheado", database.expenseDao().observeById("e-cached").first()?.description)
        assertEquals(listOf("Cacheado"), cachedDescriptions())
    }

    @Test
    fun `observeExpenses serves the cache refreshed from the network with shares`() = runTest {
        coEvery { api.list("g-1", 20, null) } returns Response.success(
            PageDto(
                items = listOf(expenseDto("e-1", shares = listOf(shareDto("u-1", 4250)))),
                nextCursor = null,
                hasMore = false,
            ),
        )

        val snapshot = withContext(Dispatchers.IO) { repository.observeExpenses("g-1").asSnapshot() }

        assertEquals(listOf("e-1"), snapshot.map { it.id })
        assertEquals(1, database.expenseDao().observeShares("e-1").first().size)
    }

    private suspend fun cacheExpense(dto: ExpenseDto) {
        // Reutiliza el camino real de escritura en cachǸ a travǸs de refreshExpense.
        coEvery { api.get(dto.id) } returns Response.success(dto)
        repository.refreshExpense(dto.id)
    }

    private fun draft(
        version: Long? = null,
        category: String? = "Comida",
        notes: String? = null,
        splitType: SplitType = SplitType.PERCENTAGE,
        shares: List<ExpenseShareInput> = listOf(
            ExpenseShareInput("u-1", percentage = BigDecimal("50")),
            ExpenseShareInput("u-2", percentage = BigDecimal("50")),
        ),
    ) = ExpenseDraft(
        description = "Cena",
        amountMinor = 4250,
        paidByUserId = "u-1",
        splitType = splitType,
        expenseDate = "2026-02-01",
        shares = shares,
        category = category,
        notes = notes,
        version = version,
    )

    private fun expenseDto(
        id: String,
        groupId: String = "g-1",
        description: String = "Cena",
        version: Long = 1,
        shares: List<ExpenseShareDto> = emptyList(),
    ) = ExpenseDto(
        id = id,
        groupId = groupId,
        description = description,
        amountMinor = 4250,
        currency = "EUR",
        paidByUserId = "u-1",
        splitType = "PERCENTAGE",
        category = "Comida",
        expenseDate = "2026-02-01",
        notes = null,
        createdBy = "u-1",
        createdAt = "2026-02-01T21:00:00Z",
        version = version,
        shares = shares,
    )

    private fun shareDto(userId: String, amountMinor: Long) = ExpenseShareDto(
        userId = userId,
        amountMinor = amountMinor,
        percentage = BigDecimal("50"),
    )

    private fun expenseEntity(id: String, description: String) = ExpenseEntity(
        id = id,
        groupId = "g-1",
        description = description,
        amountMinor = 4250,
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

    private fun <T> problemResponse(code: Int, title: String, detail: String): Response<T> = Response.error(
        code,
        """{"title":"$title","status":$code,"detail":"$detail"}"""
            .toResponseBody("application/problem+json".toMediaType()),
    )

    private suspend fun cachedIds(groupId: String = "g-1"): List<String> {
        val params = PagingSource.LoadParams.Refresh<Int>(key = null, loadSize = 50, placeholdersEnabled = false)
        val page = database.expenseDao().pagingSource(groupId).load(params) as PagingSource.LoadResult.Page
        return page.data.map { it.id }
    }

    private suspend fun cachedDescriptions(): List<String> {
        val params = PagingSource.LoadParams.Refresh<Int>(key = null, loadSize = 50, placeholdersEnabled = false)
        val page = database.expenseDao().pagingSource("g-1").load(params) as PagingSource.LoadResult.Page
        return page.data.map { it.description }
    }
}
