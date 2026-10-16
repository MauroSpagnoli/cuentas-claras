package app.cuentasclaras.feature.balances.history

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.GroupRole
import app.cuentasclaras.domain.model.MemberStatus
import app.cuentasclaras.domain.model.Settlement
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.usecase.balances.ObserveSettlementsUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveMembersUseCase
import app.cuentasclaras.domain.usecase.groups.RefreshMembersUseCase
import app.cuentasclaras.feature.balances.BalancesRoutes
import app.cuentasclaras.feature.balances.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class SettlementHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val observeSettlements = mockk<ObserveSettlementsUseCase>()
    private val observeMembers = mockk<ObserveMembersUseCase>()
    private val refreshMembers = mockk<RefreshMembersUseCase>()

    private fun member(id: String, name: String) = GroupMember(
        userId = id,
        displayName = name,
        email = "$id@example.com",
        role = GroupRole.MEMBER,
        status = MemberStatus.ACTIVE,
    )

    private fun createViewModel(): SettlementHistoryViewModel {
        every { observeSettlements("g-1") } returns flowOf(PagingData.empty<Settlement>())
        every { observeMembers("g-1") } returns flowOf(
            listOf(member("u-1", "Ana"), member("u-2", "Beto")),
        )
        coEvery { refreshMembers("g-1") } returns AppResult.Success(Unit)
        return SettlementHistoryViewModel(
            observeSettlements,
            observeMembers,
            refreshMembers,
            SavedStateHandle(mapOf(BalancesRoutes.ARG_GROUP_ID to "g-1")),
        )
    }

    @Test
    fun `reads the group id taken from the route`() = runTest(mainDispatcherRule.dispatcher) {
        assertEquals("g-1", createViewModel().groupId)
    }

    @Test
    fun `needs the group id in the route`() {
        assertThrows(IllegalArgumentException::class.java) {
            SettlementHistoryViewModel(
                observeSettlements,
                observeMembers,
                refreshMembers,
                SavedStateHandle(),
            )
        }
    }

    @Test
    fun `exposes the member names used by the rows`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Ana", viewModel.state.value.memberName("u-1"))
        assertEquals("Beto", viewModel.state.value.memberName("u-2"))
        assertNull(viewModel.state.value.memberName("u-9"))
    }

    @Test
    fun `refreshes the members and reads the settlement pages when it starts`() =
        runTest(mainDispatcherRule.dispatcher) {
            createViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) { refreshMembers("g-1") }
            verify(exactly = 1) { observeSettlements("g-1") }
        }
}
