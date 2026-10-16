package app.cuentasclaras.core.network

import app.cuentasclaras.core.network.api.BalancesApi
import app.cuentasclaras.core.network.dto.CreateSettlementRequestDto
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.result.AppResult
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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

class BalancesApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: BalancesApi

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BalancesApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `balances parses the net amounts and the pairs`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """
                {
                  "currency": "EUR",
                  "members": [
                    {"userId":"u-1","displayName":"Ana","netMinor":1500,"currency":"EUR"},
                    {"userId":"u-2","displayName":"Beto","netMinor":-1500,"currency":"EUR"}
                  ],
                  "pairs": [
                    {"fromUserId":"u-2","toUserId":"u-1","amountMinor":1500}
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = safeApiCall(json) { api.balances("g-1") }

        val dto = (result as AppResult.Success).value
        assertEquals("EUR", dto.currency)
        assertEquals(listOf("u-1", "u-2"), dto.members.map { it.userId })
        assertEquals(1500L, dto.members[0].netMinor)
        assertEquals(-1500L, dto.members[1].netMinor)
        assertEquals("u-2", dto.pairs.single().fromUserId)
        assertEquals("u-1", dto.pairs.single().toUserId)
        assertEquals(1500L, dto.pairs.single().amountMinor)
        assertEquals("/api/v1/groups/g-1/balances", server.takeRequest().path)
    }

    @Test
    fun `settlement plan keeps the algorithm and the steps`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """
                {
                  "currency": "EUR",
                  "algorithm": "EXACT",
                  "transactionCount": 1,
                  "steps": [
                    {"fromUserId":"u-2","toUserId":"u-1","amountMinor":1500}
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = safeApiCall(json) { api.settlementPlan("g-1") }

        val dto = (result as AppResult.Success).value
        assertEquals("EXACT", dto.algorithm)
        assertEquals(1, dto.transactionCount)
        assertEquals("u-2", dto.steps.single().fromUserId)
        assertEquals("/api/v1/groups/g-1/settlement-plan", server.takeRequest().path)
    }

    @Test
    fun `register posts the payment and omits the currency`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """
                {
                  "id": "s-1",
                  "groupId": "g-1",
                  "fromUserId": "u-2",
                  "toUserId": "u-1",
                  "amountMinor": 1250,
                  "currency": "EUR",
                  "settledOn": "2026-02-01",
                  "note": "Bizum",
                  "createdAt": "2026-02-01T10:00:00Z"
                }
                """.trimIndent(),
                code = 201,
            ),
        )

        val result = safeApiCall(json) {
            api.registerSettlement(
                groupId = "g-1",
                body = CreateSettlementRequestDto(
                    fromUserId = "u-2",
                    toUserId = "u-1",
                    amountMinor = 1250,
                    settledOn = "2026-02-01",
                    note = "Bizum",
                ),
            )
        }

        assertEquals("s-1", (result as AppResult.Success).value.id)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/groups/g-1/settlements", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"fromUserId\":\"u-2\""))
        assertTrue(body.contains("\"toUserId\":\"u-1\""))
        assertTrue(body.contains("\"amountMinor\":1250"))
        assertTrue(body.contains("\"settledOn\":\"2026-02-01\""))
        assertTrue(body.contains("\"note\":\"Bizum\""))
        assertTrue(!body.contains("\"currency\""))
    }

    @Test
    fun `a missing group maps to NotFound`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"title":"Not Found","status":404,"detail":"el grupo no existe"}""",
                code = 404,
                contentType = "application/problem+json",
            ),
        )

        val result = safeApiCall(json) { api.balances("g-9") }

        assertEquals(AppError.NotFound, (result as AppResult.Failure).error)
    }

    @Test
    fun `settlements parses the page and sends the cursor`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """
                {
                  "items": [
                    {
                      "id": "s-1",
                      "groupId": "g-1",
                      "fromUserId": "u-2",
                      "toUserId": "u-1",
                      "amountMinor": 1500,
                      "currency": "EUR",
                      "settledOn": "2026-02-01",
                      "note": "Bizum",
                      "createdAt": "2026-02-01T10:00:00Z"
                    }
                  ],
                  "nextCursor": "cursor-2",
                  "hasMore": true
                }
                """.trimIndent(),
            ),
        )

        val result = safeApiCall(json) { api.settlements("g-1", limit = 20, cursor = "cursor-1") }

        val page = (result as AppResult.Success).value
        assertEquals(1, page.items.size)
        assertEquals("s-1", page.items.single().id)
        assertEquals(1500L, page.items.single().amountMinor)
        assertEquals("cursor-2", page.nextCursor)
        assertTrue(page.hasMore)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals(
            "/api/v1/groups/g-1/settlements?limit=20&cursor=cursor-1",
            recorded.path,
        )
    }

    @Test
    fun `an empty settlements page stops paginating`() = runBlocking {
        server.enqueue(jsonResponse("""{"items":[],"hasMore":false}"""))

        val result = safeApiCall(json) { api.settlements("g-1") }

        val page = (result as AppResult.Success).value
        assertTrue(page.items.isEmpty())
        assertEquals(null, page.nextCursor)
        assertTrue(!page.hasMore)
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
