package app.cuentasclaras.core.network

import app.cuentasclaras.core.network.api.ExpensesApi
import app.cuentasclaras.core.network.dto.CreateExpenseRequestDto
import app.cuentasclaras.core.network.dto.ExpenseShareInputDto
import app.cuentasclaras.core.network.dto.UpdateExpenseRequestDto
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.result.AppResult
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class ExpensesApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ExpensesApi

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ExpensesApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `list sends the cursor and parses the page`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """
                {
                  "items": [
                    {
                      "id": "e-1",
                      "groupId": "g-1",
                      "description": "Cena",
                      "amountMinor": 4250,
                      "currency": "EUR",
                      "paidByUserId": "u-1",
                      "splitType": "PERCENTAGE",
                      "category": "Comida",
                      "expenseDate": "2026-02-01",
                      "notes": "Sin propina",
                      "createdBy": "u-1",
                      "createdAt": "2026-02-01T21:00:00Z",
                      "version": 1,
                      "shares": [
                        {"userId":"u-1","amountMinor":1417,"percentage":33.33},
                        {"userId":"u-2","amountMinor":1417,"percentage":33.33},
                        {"userId":"u-3","amountMinor":1416,"percentage":33.34}
                      ]
                    }
                  ],
                  "nextCursor": "cursor-2",
                  "hasMore": true
                }
                """.trimIndent(),
            ),
        )

        val result = safeApiCall(json) { api.list(groupId = "g-1", limit = 20, cursor = null) }

        val page = (result as AppResult.Success).value
        val expense = page.items.single()
        assertEquals("e-1", expense.id)
        assertEquals(4250L, expense.amountMinor)
        assertEquals("PERCENTAGE", expense.splitType)
        assertEquals(3, expense.shares.size)
        assertEquals("cursor-2", page.nextCursor)
        assertTrue(page.hasMore)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/v1/groups/g-1/expenses?limit=20", recorded.path)
    }

    @Test
    fun `list with cursor keeps the query`() = runBlocking {
        server.enqueue(jsonResponse("""{"items":[],"hasMore":false}"""))

        safeApiCall(json) { api.list(groupId = "g-1", limit = 20, cursor = "abc==") }

        assertEquals("/api/v1/groups/g-1/expenses?limit=20&cursor=abc%3D%3D", server.takeRequest().path)
    }

    @Test
    fun `big decimals survive the round trip`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """
                {
                  "id": "e-1",
                  "shares": [
                    {"userId":"u-1","amountMinor":333,"weight":0.3333333333},
                    {"userId":"u-2","amountMinor":333,"percentage":33.33}
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = safeApiCall(json) { api.get("e-1") }

        val shares = (result as AppResult.Success).value.shares
        assertEquals(BigDecimal("0.3333333333"), shares[0].weight)
        assertEquals(BigDecimal("33.33"), shares[1].percentage)
        assertEquals(null, shares[0].percentage)
    }

    @Test
    fun `create posts the split and omits the currency`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"id":"e-9","groupId":"g-1","description":"Cena","amountMinor":1000,"currency":"EUR","version":0}""",
                code = 201,
            ),
        )

        val result = safeApiCall(json) {
            api.create(
                groupId = "g-1",
                body = CreateExpenseRequestDto(
                    description = "Cena",
                    amountMinor = 1000,
                    paidByUserId = "u-1",
                    splitType = "PERCENTAGE",
                    expenseDate = "2026-02-01",
                    shares = listOf(
                        ExpenseShareInputDto("u-1", percentage = BigDecimal("33.33")),
                        ExpenseShareInputDto("u-2", percentage = BigDecimal("66.67")),
                    ),
                ),
            )
        }

        assertEquals("e-9", (result as AppResult.Success).value.id)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/groups/g-1/expenses", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"splitType\":\"PERCENTAGE\""))
        assertTrue(body.contains("\"percentage\":33.33"))
        assertTrue(!body.contains("\"currency\""))
    }

    @Test
    fun `update patches version and clears optional fields`() = runBlocking {
        server.enqueue(
            jsonResponse("""{"id":"e-1","groupId":"g-1","version":2}"""),
        )

        val result = safeApiCall(json) {
            api.update(
                expenseId = "e-1",
                body = UpdateExpenseRequestDto(
                    description = "Cena",
                    amountMinor = 4250,
                    paidByUserId = "u-1",
                    splitType = "EQUAL",
                    expenseDate = "2026-02-01",
                    shares = listOf(ExpenseShareInputDto("u-1"), ExpenseShareInputDto("u-2")),
                    category = "",
                    notes = "",
                    version = 1,
                ),
            )
        }

        assertEquals(2L, (result as AppResult.Success).value.version)

        val recorded = server.takeRequest()
        assertEquals("PATCH", recorded.method)
        assertEquals("/api/v1/expenses/e-1", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"version\":1"))
        assertTrue(body.contains("\"category\":\"\""))
        assertTrue(body.contains("\"notes\":\"\""))
        assertTrue(!body.contains("\"percentage\""))
    }

    @Test
    fun `delete accepts an empty 204`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = safeUnitApiCall(json) { api.delete("e-1") }

        assertTrue(result is AppResult.Success)
        assertEquals("/api/v1/expenses/e-1", server.takeRequest().path)
    }

    @Test
    fun `conflict maps to Conflict with detail`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"title":"Conflict","status":409,"detail":"Version desactualizada","code":"STALE_VERSION"}""",
                code = 409,
                contentType = "application/problem+json",
            ),
        )

        val result = safeApiCall(json) {
            api.update(
                expenseId = "e-1",
                body = UpdateExpenseRequestDto(
                    description = "Cena",
                    amountMinor = 4250,
                    paidByUserId = "u-1",
                    splitType = "EQUAL",
                    expenseDate = "2026-02-01",
                    shares = listOf(ExpenseShareInputDto("u-1")),
                    category = "",
                    notes = "",
                    version = 1,
                ),
            )
        }

        assertEquals(AppError.Conflict("Version desactualizada"), (result as AppResult.Failure).error)
    }

    private fun jsonResponse(
        body: String,
        code: Int = 200,
        contentType: String = "application/json",
    ): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", contentType)
        .setBody(body)
}
