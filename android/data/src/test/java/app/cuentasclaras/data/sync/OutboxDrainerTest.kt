package app.cuentasclaras.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cuentasclaras.core.database.CuentasClarasDatabase
import app.cuentasclaras.core.database.entity.ExpenseEntity
import app.cuentasclaras.core.database.entity.OutboxEntity
import app.cuentasclaras.core.network.api.ExpensesApi
import app.cuentasclaras.core.network.dto.CreateExpenseRequestDto
import app.cuentasclaras.core.network.dto.ExpenseDto
import app.cuentasclaras.core.network.dto.ExpenseShareInputDto
import app.cuentasclaras.core.network.dto.UpdateExpenseRequestDto
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
class OutboxDrainerTest {

    private lateinit var database: CuentasClarasDatabase
    private val api: ExpensesApi = mockk()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private lateinit var drainer: OutboxDrainer

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CuentasClarasDatabase::class.java,
        ).build()
        drainer = OutboxDrainer(api, database, database.outboxDao(), json, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `create success replaces the placeholder with the server expense`() = runTest {
        database.expenseDao().upsert(expenseEntity("local-1", pendingOperation = "CREATE"))
        database.outboxDao().enqueue(createRow("op-1", "local-1"))
        coEvery { api.create("g-1", any(), "op-1") } returns Response.success(expenseDto("e-1"))

        val outcome = drainer.drain()

        assertEquals(OutboxDrainer.Outcome.COMPLETE, outcome)
        assertNull(database.expenseDao().getById("local-1"))
        assertEquals("Cena", database.expenseDao().getById("e-1")?.description)
        assertNull(database.expenseDao().getById("e-1")?.pendingOperation)
        assertEquals(0, database.outboxDao().countPending())
    }

    @Test
    fun `a network failure keeps the operation pending`() = runTest {
        database.outboxDao().enqueue(createRow("op-1", "local-1"))
        coEvery { api.create("g-1", any(), "op-1") } throws IOException("sin red")

        assertEquals(OutboxDrainer.Outcome.RETRY, drainer.drain())

        val row = database.outboxDao().pending(10).single()
        assertEquals(1, row.attempts)
        assertEquals("NETWORK", row.lastErrorCode)
    }

    @Test
    fun `a validation failure marks the operation as failed`() = runTest {
        database.outboxDao().enqueue(createRow("op-1", "local-1"))
        coEvery { api.create("g-1", any(), "op-1") } returns
            problemResponse(422, "Unprocessable", "reparto invalido")

        assertEquals(OutboxDrainer.Outcome.COMPLETE, drainer.drain())

        assertEquals(0, database.outboxDao().countPending())
        val row = database.outboxDao().forEntity("local-1").single()
        assertEquals("FAILED", row.status)
        assertEquals("VALIDATION", row.lastErrorCode)
    }

    @Test
    fun `a version conflict on update marks it failed and reloads the server version`() = runTest {
        database.expenseDao().upsert(expenseEntity("e-1", pendingOperation = "UPDATE"))
        database.outboxDao().enqueue(updateRow("op-1", "e-1"))
        coEvery { api.update("e-1", any(), "op-1") } returns
            problemResponse(409, "Conflict", "Version desactualizada")
        coEvery { api.get("e-1") } returns Response.success(expenseDto("e-1", description = "Servidor"))

        assertEquals(OutboxDrainer.Outcome.COMPLETE, drainer.drain())

        val row = database.outboxDao().forEntity("e-1").single()
        assertEquals("FAILED", row.status)
        assertEquals("CONFLICT", row.lastErrorCode)
        // La caché muestra la versión vigente del servidor.
        assertEquals("Servidor", database.expenseDao().getById("e-1")?.description)
        assertNull(database.expenseDao().getById("e-1")?.pendingOperation)
    }

    @Test
    fun `update success caches the server response and clears the outbox`() = runTest {
        database.expenseDao().upsert(expenseEntity("e-1", pendingOperation = "UPDATE"))
        database.outboxDao().enqueue(updateRow("op-1", "e-1"))
        coEvery { api.update("e-1", any(), "op-1") } returns
            Response.success(expenseDto("e-1", description = "Servidor"))

        assertEquals(OutboxDrainer.Outcome.COMPLETE, drainer.drain())

        assertEquals("Servidor", database.expenseDao().getById("e-1")?.description)
        assertEquals(0, database.outboxDao().countPending())
    }

    @Test
    fun `delete success clears the outbox row`() = runTest {
        database.outboxDao().enqueue(deleteRow("op-1", "e-1"))
        coEvery { api.delete("e-1", "op-1") } returns Response.success(Unit)

        assertEquals(OutboxDrainer.Outcome.COMPLETE, drainer.drain())

        assertEquals(0, database.outboxDao().countPending())
    }

    private fun createRow(id: String, entityId: String): OutboxEntity = OutboxEntity(
        id = id,
        entityType = OutboxEntity.ENTITY_EXPENSE,
        entityId = entityId,
        groupId = "g-1",
        operation = OutboxEntity.OPERATION_CREATE,
        payload = json.encodeToString(
            CreateExpenseRequestDto(
                description = "Cena",
                amountMinor = 4250,
                paidByUserId = "u-1",
                splitType = "PERCENTAGE",
                expenseDate = "2026-02-01",
                shares = listOf(ExpenseShareInputDto(userId = "u-1")),
            ),
        ),
        status = OutboxEntity.STATUS_PENDING,
        createdAt = 1L,
    )

    private fun updateRow(id: String, entityId: String): OutboxEntity = OutboxEntity(
        id = id,
        entityType = OutboxEntity.ENTITY_EXPENSE,
        entityId = entityId,
        groupId = "g-1",
        operation = OutboxEntity.OPERATION_UPDATE,
        payload = json.encodeToString(
            UpdateExpenseRequestDto(
                description = "Cena",
                amountMinor = 4250,
                paidByUserId = "u-1",
                splitType = "PERCENTAGE",
                expenseDate = "2026-02-01",
                shares = listOf(ExpenseShareInputDto(userId = "u-1")),
                category = "",
                notes = "",
                version = 1,
            ),
        ),
        status = OutboxEntity.STATUS_PENDING,
        createdAt = 1L,
    )

    private fun deleteRow(id: String, entityId: String): OutboxEntity = OutboxEntity(
        id = id,
        entityType = OutboxEntity.ENTITY_EXPENSE,
        entityId = entityId,
        groupId = "g-1",
        operation = OutboxEntity.OPERATION_DELETE,
        payload = "",
        status = OutboxEntity.STATUS_PENDING,
        createdAt = 1L,
    )

    private fun expenseEntity(id: String, pendingOperation: String? = null) = ExpenseEntity(
        id = id,
        groupId = "g-1",
        description = "Cena",
        amountMinor = 4250,
        currency = "EUR",
        paidByUserId = "u-1",
        splitType = "PERCENTAGE",
        category = null,
        expenseDate = "2026-02-01",
        notes = null,
        createdBy = "u-1",
        createdAt = null,
        version = 1,
        pendingOperation = pendingOperation,
    )

    private fun expenseDto(
        id: String,
        description: String = "Cena",
    ) = ExpenseDto(
        id = id,
        groupId = "g-1",
        description = description,
        amountMinor = 4250,
        currency = "EUR",
        paidByUserId = "u-1",
        splitType = "PERCENTAGE",
        category = null,
        expenseDate = "2026-02-01",
        notes = null,
        createdBy = "u-1",
        createdAt = "2026-02-01T21:00:00Z",
        version = 2,
        shares = emptyList(),
    )

    private fun <T> problemResponse(code: Int, title: String, detail: String): Response<T> = Response.error(
        code,
        """{"title":"$title","status":$code,"detail":"$detail"}"""
            .toResponseBody("application/problem+json".toMediaType()),
    )
}
