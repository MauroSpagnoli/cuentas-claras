package app.cuentasclaras.feature.expenses.list

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.GroupRole
import app.cuentasclaras.domain.model.MemberStatus
import app.cuentasclaras.domain.model.OutboxOperation
import app.cuentasclaras.domain.model.SyncFailure
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.usecase.expenses.ObserveExpensesUseCase
import app.cuentasclaras.domain.usecase.expenses.ObserveSyncFailuresUseCase
import app.cuentasclaras.domain.usecase.expenses.RetrySyncUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveGroupUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveMembersUseCase
import app.cuentasclaras.domain.usecase.groups.RefreshGroupUseCase
import app.cuentasclaras.domain.usecase.groups.RefreshMembersUseCase
import app.cuentasclaras.feature.expenses.ExpensesRoutes
import app.cuentasclaras.feature.expenses.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExpensesListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val observeExpenses = mockk<ObserveExpensesUseCase>()
    private val observeGroup = mockk<ObserveGroupUseCase>()
    private val observeMembers = mockk<ObserveMembersUseCase>()
    private val refreshGroup = mockk<RefreshGroupUseCase>()
    private val refreshMembers = mockk<RefreshMembersUseCase>()
    private val observeSyncFailures = mockk<ObserveSyncFailuresUseCase>()
    private val retrySync = mockk<RetrySyncUseCase>()

    private val ana = member("u-1", "Ana", GroupRole.OWNER)
    private val beto = member("u-2", "Beto", GroupRole.MEMBER)

    private fun createViewModel(failures: List<SyncFailure> = emptyList()): ExpensesListViewModel {
        every { observeExpenses("g-1") } returns flowOf(PagingData.empty())
        every { observeGroup("g-1") } returns flowOf(group("Piso"))
        every { observeMembers("g-1") } returns flowOf(listOf(ana, beto))
        every { observeSyncFailures("g-1") } returns flowOf(failures)
        coEvery { refreshGroup("g-1") } returns AppResult.Success(group("Piso"))
        coEvery { refreshMembers("g-1") } returns AppResult.Success(Unit)
        return ExpensesListViewModel(
            observeExpenses,
            observeGroup,
            observeMembers,
            observeSyncFailures,
            retrySync,
            refreshGroup,
            refreshMembers,
            SavedStateHandle(mapOf(ExpensesRoutes.ARG_GROUP_ID to "g-1")),
        )
    }


    @Test
    fun `reads the group name and the members from the cache`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Piso", viewModel.state.value.groupName)
        assertEquals(listOf("u-1", "u-2"), viewModel.state.value.members.map { it.userId })
        assertEquals("Beto", viewModel.state.value.memberName("u-2"))
        assertNull(viewModel.state.value.memberName("u-9"))
    }

    @Test
    fun `refreshes the group and the members when it starts`() = runTest(mainDispatcherRule.dispatcher) {
        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { refreshGroup("g-1") }
        coVerify(exactly = 1) { refreshMembers("g-1") }
    }

    @Test
    fun `exposes the group id taken from the route`() = runTest(mainDispatcherRule.dispatcher) {
        assertEquals("g-1", createViewModel().groupId)
    }

    @Test
    fun `exposes the outbox sync failures`() = runTest(mainDispatcherRule.dispatcher) {
        val failure = SyncFailure("e-1", OutboxOperation.UPDATE, AppError.Conflict("desactualizado"))

        val viewModel = createViewModel(failures = listOf(failure))
        advanceUntilIdle()

        assertEquals(listOf(failure), viewModel.state.value.syncFailures)
        assertTrue(viewModel.state.value.hasSyncFailures)
    }

    @Test
    fun `retrySync relanza el drenado del outbox`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { retrySync("g-1") } returns Unit
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.retrySync()
        advanceUntilIdle()

        coVerify(exactly = 1) { retrySync("g-1") }
    }

    @Test
    fun `needs the group id in the route`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExpensesListViewModel(
                observeExpenses,
                observeGroup,
                observeMembers,
                observeSyncFailures,
                retrySync,
                refreshGroup,
                refreshMembers,
                SavedStateHandle(),
            )
        }
    }

    private fun group(name: String) = Group(id = "g-1", name = name, currency = "EUR")

    private fun member(userId: String, displayName: String, role: GroupRole) = GroupMember(
        userId = userId,
        displayName = displayName,
        email = "$userId@example.com",
        role = role,
        status = MemberStatus.ACTIVE,
    )
}
