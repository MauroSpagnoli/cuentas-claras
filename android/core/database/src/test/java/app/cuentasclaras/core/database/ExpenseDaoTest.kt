package app.cuentasclaras.core.database

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cuentasclaras.core.database.entity.ExpenseEntity
import app.cuentasclaras.core.database.entity.ExpenseShareEntity
import app.cuentasclaras.core.database.entity.ExpenseSyncStateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExpenseDaoTest {

    private lateinit var database: CuentasClarasDatabase

    private fun expense(
        id: String,
        groupId: String = "A",
        expenseDate: String = "2026-02-01",
        description: String = "Cena",
    ) = ExpenseEntity(
        id = id,
        groupId = groupId,
        description = description,
        amountMinor = 4250,
        currency = "EUR",
        paidByUserId = "u-1",
        splitType = "EQUAL",
        category = null,
        expenseDate = expenseDate,
        notes = null,
        createdBy = "u-1",
        createdAt = "2026-02-01T21:00:00Z",
        version = 1,
    )

    private fun share(
        expenseId: String,
        userId: String,
        amountMinor: Long = 2125,
        percentage: String? = null,
        weight: String? = null,
    ) = ExpenseShareEntity(
        expenseId = expenseId,
        userId = userId,
        amountMinor = amountMinor,
        percentage = percentage,
        weight = weight,
    )

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CuentasClarasDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `paging source filters by group and orders by date then id desc`() = runTest {
        val dao = database.expenseDao()
        dao.upsertAll(
            listOf(
                expense("e-1", expenseDate = "2026-02-01"),
                expense("e-2", expenseDate = "2026-02-03"),
                expense("e-3", expenseDate = "2026-02-03"),
                expense("e-other", groupId = "B", expenseDate = "2026-02-09"),
            ),
        )

        val page = dao.pagingSource("A").load(refresh()) as PagingSource.LoadResult.Page

        assertEquals(listOf("e-3", "e-2", "e-1"), page.data.map { it.id })
    }

    @Test
    fun `observe by id emits null when the expense is not cached`() = runTest {
        assertNull(database.expenseDao().observeById("missing").first())
    }

    @Test
    fun `upsert replaces the cached expense`() = runTest {
        val dao = database.expenseDao()
        dao.upsert(expense("e-1"))
        dao.upsert(expense("e-1", description = "Cena larga").copy(version = 2))

        val cached = dao.observeById("e-1").first()
        assertEquals("Cena larga", cached?.description)
        assertEquals(2L, cached?.version)
    }

    @Test
    fun `shares are keyed by expense and user and keep the exact decimals`() = runTest {
        val dao = database.expenseDao()
        dao.upsert(expense("e-1"))
        dao.upsertShares(
            listOf(
                share("e-1", "u-2", percentage = "33.33"),
                share("e-1", "u-1", amountMinor = 1417, percentage = "33.33"),
                share("e-1", "u-1", amountMinor = 1417, weight = "0.3333333333"),
            ),
        )

        val shares = dao.observeShares("e-1").first()

        assertEquals(listOf("u-1", "u-2"), shares.map { it.userId })
        assertEquals("0.3333333333", shares[0].weight)
        assertNull(shares[0].percentage)
    }

    @Test
    fun `clear shares removes only that expense`() = runTest {
        val dao = database.expenseDao()
        dao.upsertShares(listOf(share("e-1", "u-1"), share("e-2", "u-1")))

        dao.clearShares("e-1")

        assertEquals(0, dao.observeShares("e-1").first().size)
        assertEquals(1, dao.observeShares("e-2").first().size)
    }

    @Test
    fun `clear expenses removes only that group`() = runTest {
        val dao = database.expenseDao()
        dao.upsertAll(listOf(expense("e-1"), expense("e-2", groupId = "B")))

        dao.clearExpenses("A")

        assertEquals(0, cachedIds(dao, "A").size)
        assertEquals(listOf("e-2"), cachedIds(dao, "B"))
    }

    @Test
    fun `clear shares of a group removes the shares of each of its expenses`() = runTest {
        val dao = database.expenseDao()
        dao.upsertAll(listOf(expense("e-1"), expense("e-2"), expense("e-3", groupId = "B")))
        dao.upsertShares(listOf(share("e-1", "u-1"), share("e-2", "u-1"), share("e-3", "u-1")))

        dao.clearSharesOfGroup("A")

        assertEquals(0, dao.observeShares("e-1").first().size)
        assertEquals(0, dao.observeShares("e-2").first().size)
        assertEquals(1, dao.observeShares("e-3").first().size)
    }

    @Test
    fun `delete expense removes only that row`() = runTest {
        val dao = database.expenseDao()
        dao.upsertAll(listOf(expense("e-1"), expense("e-2")))

        dao.deleteExpense("e-2")

        assertEquals(listOf("e-1"), cachedIds(dao, "A"))
    }

    @Test
    fun `sync state keeps one row per group`() = runTest {
        val dao = database.expenseDao()
        assertNull(dao.syncState("A"))

        dao.upsertSyncState(ExpenseSyncStateEntity(groupId = "A", nextCursor = "c1", endReached = false))
        dao.upsertSyncState(ExpenseSyncStateEntity(groupId = "B", nextCursor = "c9", endReached = false))
        dao.upsertSyncState(ExpenseSyncStateEntity(groupId = "A", nextCursor = null, endReached = true))

        assertEquals("A", dao.syncState("A")?.groupId)
        assertNull(dao.syncState("A")?.nextCursor)
        assertEquals(true, dao.syncState("A")?.endReached)
        assertEquals("c9", dao.syncState("B")?.nextCursor)
    }

    private suspend fun cachedIds(dao: app.cuentasclaras.core.database.dao.ExpenseDao, groupId: String): List<String> =
        (dao.pagingSource(groupId).load(refresh()) as PagingSource.LoadResult.Page).data.map { it.id }

    private fun refresh(pageSize: Int = 50) = PagingSource.LoadParams.Refresh<Int>(
        key = null,
        loadSize = pageSize,
        placeholdersEnabled = false,
    )
}
