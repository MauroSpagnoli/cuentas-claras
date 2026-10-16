package app.cuentasclaras.data.repository

import app.cuentasclaras.core.network.api.BalancesApi
import app.cuentasclaras.core.network.dto.BalancesDto
import app.cuentasclaras.core.network.dto.CreateSettlementRequestDto
import app.cuentasclaras.core.network.dto.MemberBalanceDto
import app.cuentasclaras.core.network.dto.PairwiseDebtDto
import app.cuentasclaras.core.network.dto.SettlementDto
import app.cuentasclaras.core.network.dto.SettlementPlanDto
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.SettlementDraft
import app.cuentasclaras.domain.result.AppResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class BalanceRepositoryImplTest {

    private val api = mockk<BalancesApi>()
    private val json = Json { ignoreUnknownKeys = true }
    private val repository = BalanceRepositoryImpl(api, json, UnconfinedTestDispatcher())

    @Test
    fun `loadBalances maps the dto to the domain`() = runTest {
        coEvery { api.balances("g-1") } returns Response.success(
            BalancesDto(
                currency = "EUR",
                members = listOf(
                    MemberBalanceDto("u-1", "Ana", 1500, "EUR"),
                    MemberBalanceDto("u-2", "Beto", -1500, "EUR"),
                ),
                pairs = listOf(PairwiseDebtDto("u-2", "u-1", 1500)),
            ),
        )

        val result = repository.loadBalances("g-1")

        val balances = (result as AppResult.Success).value
        assertEquals("EUR", balances.currency)
        assertEquals("Ana", balances.members.first().displayName)
        assertEquals(-1500L, balances.members[1].netMinor)
        assertEquals(1500L, balances.pairs.single().amountMinor)
    }

    @Test
    fun `loadSettlementPlan maps the algorithm and the steps`() = runTest {
        coEvery { api.settlementPlan("g-1") } returns Response.success(
            SettlementPlanDto(
                currency = "EUR",
                algorithm = "GREEDY",
                transactionCount = 1,
                steps = listOf(PairwiseDebtDto("u-2", "u-1", 1500)),
            ),
        )

        val result = repository.loadSettlementPlan("g-1")

        val plan = (result as AppResult.Success).value
        assertEquals("GREEDY", plan.algorithm)
        assertEquals(1, plan.transactionCount)
        assertEquals("u-2", plan.steps.single().fromUserId)
    }

    @Test
    fun `registerSettlement sends the draft without currency and maps the response`() = runTest {
        coEvery { api.registerSettlement("g-1", any()) } returns Response.success(
            SettlementDto(
                id = "s-1",
                groupId = "g-1",
                fromUserId = "u-2",
                toUserId = "u-1",
                amountMinor = 1250,
                currency = "EUR",
                settledOn = "2026-02-01",
                note = "Bizum",
                createdAt = "2026-02-01T10:00:00Z",
            ),
        )
        val body = slot<CreateSettlementRequestDto>()

        val result = repository.registerSettlement(
            "g-1",
            SettlementDraft(
                fromUserId = "u-2",
                toUserId = "u-1",
                amountMinor = 1250,
                settledOn = "2026-02-01",
                note = "Bizum",
            ),
        )

        val settlement = (result as AppResult.Success).value
        assertEquals("s-1", settlement.id)
        assertEquals(1250L, settlement.amountMinor)
        coVerify(exactly = 1) { api.registerSettlement("g-1", capture(body)) }
        assertEquals("u-2", body.captured.fromUserId)
        assertEquals(1250L, body.captured.amountMinor)
        assertEquals("2026-02-01", body.captured.settledOn)
        assertNull(body.captured.currency)
    }

    @Test
    fun `a failed call maps the problem to the domain error`() = runTest {
        coEvery { api.balances("g-1") } returns problemResponse(409, "Conflict", "grupo modificado")

        val result = repository.loadBalances("g-1")

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.Conflict("grupo modificado"), (result as AppResult.Failure).error)
    }

    private fun <T> problemResponse(code: Int, title: String, detail: String): Response<T> = Response.error(
        code,
        """{"title":"$title","status":$code,"detail":"$detail"}"""
            .toResponseBody("application/problem+json".toMediaType()),
    )
}
