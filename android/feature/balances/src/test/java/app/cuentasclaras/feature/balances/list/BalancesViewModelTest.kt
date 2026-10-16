package app.cuentasclaras.feature.balances.list

import androidx.lifecycle.SavedStateHandle
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.GroupBalances
import app.cuentasclaras.domain.model.MemberBalance
import app.cuentasclaras.domain.model.PairwiseDebt
import app.cuentasclaras.domain.model.SettlementPlan
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.usecase.balances.LoadBalancesUseCase
import app.cuentasclaras.domain.usecase.balances.LoadSettlementPlanUseCase
import app.cuentasclaras.feature.balances.BalancesRoutes
import app.cuentasclaras.feature.balances.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class BalancesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val loadBalances = mockk<LoadBalancesUseCase>()
    private val loadSettlementPlan = mockk<LoadSettlementPlanUseCase>()

    private fun balances() = GroupBalances(
        currency = "EUR",
        members = listOf(
            MemberBalance(userId = "u-1", displayName = "Ana", netMinor = 1500, currency = "EUR"),
            MemberBalance(userId = "u-2", displayName = "Beto", netMinor = -1500, currency = "EUR"),
        ),
        pairs = listOf(PairwiseDebt(fromUserId = "u-2", toUserId = "u-1", amountMinor = 1500)),
    )

    private fun plan() = SettlementPlan(
        currency = "EUR",
        algorithm = "EXACT",
        transactionCount = 1,
        steps = listOf(PairwiseDebt(fromUserId = "u-2", toUserId = "u-1", amountMinor = 1500)),
    )

    private fun createViewModel(): BalancesViewModel {
        coEvery { loadBalances("g-1") } returns AppResult.Success(balances())
        coEvery { loadSettlementPlan("g-1") } returns AppResult.Success(plan())
        return BalancesViewModel(
            loadBalances,
            loadSettlementPlan,
            SavedStateHandle(mapOf(BalancesRoutes.ARG_GROUP_ID to "g-1")),
        )
    }

    @Test
    fun `loads the balances and the plan when it starts`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("EUR", state.currency)
        assertEquals(listOf("u-1", "u-2"), state.members.map { it.userId })
        assertEquals("Ana", state.memberName("u-1"))
        assertNull(state.memberName("u-9"))
        assertEquals(1, state.steps.size)
        assertEquals(PairwiseDebt("u-2", "u-1", 1500), state.steps.single())
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `reads the group id taken from the route`() = runTest(mainDispatcherRule.dispatcher) {
        assertEquals("g-1", createViewModel().groupId)
    }

    @Test
    fun `needs the group id in the route`() {
        assertThrows(IllegalArgumentException::class.java) {
            BalancesViewModel(loadBalances, loadSettlementPlan, SavedStateHandle())
        }
    }

    @Test
    fun `propagates the balances error over the plan one`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { loadBalances("g-1") } returns AppResult.Failure(AppError.NotFound)
        coEvery { loadSettlementPlan("g-1") } returns AppResult.Failure(AppError.Network)

        val viewModel = BalancesViewModel(
            loadBalances,
            loadSettlementPlan,
            SavedStateHandle(mapOf(BalancesRoutes.ARG_GROUP_ID to "g-1")),
        )
        advanceUntilIdle()

        assertEquals(AppError.NotFound, viewModel.state.value.error)
        assertFalse(viewModel.state.value.hasData)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `a failed plan keeps the balances already loaded`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { loadSettlementPlan("g-1") } returns AppResult.Failure(AppError.Network)

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("u-1", "u-2"), state.members.map { it.userId })
        assertEquals(1, state.steps.size)
        assertEquals(AppError.Network, state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `refresh asks the backend again for both endpoints`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 2) { loadBalances("g-1") }
        coVerify(exactly = 2) { loadSettlementPlan("g-1") }
    }

    @Test
    fun `has no data when there is neither balances nor plan`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { loadBalances("g-1") } returns AppResult.Failure(AppError.Network)
        coEvery { loadSettlementPlan("g-1") } returns AppResult.Failure(AppError.Network)

        val viewModel = BalancesViewModel(
            loadBalances,
            loadSettlementPlan,
            SavedStateHandle(mapOf(BalancesRoutes.ARG_GROUP_ID to "g-1")),
        )
        advanceUntilIdle()

        assertFalse(viewModel.state.value.hasData)
        assertFalse(viewModel.state.value.isLoading)
    }
}
