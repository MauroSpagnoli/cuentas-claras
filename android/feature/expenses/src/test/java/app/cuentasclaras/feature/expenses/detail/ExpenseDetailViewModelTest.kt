package app.cuentasclaras.feature.expenses.detail

import androidx.lifecycle.SavedStateHandle
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.GroupRole
import app.cuentasclaras.domain.model.MemberStatus
import app.cuentasclaras.domain.model.SplitType
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.usecase.expenses.DeleteExpenseUseCase
import app.cuentasclaras.domain.usecase.expenses.ObserveExpenseUseCase
import app.cuentasclaras.domain.usecase.expenses.RefreshExpenseUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveMembersUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExpenseDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val observeExpense = mockk<ObserveExpenseUseCase>()
    private val observeMembers = mockk<ObserveMembersUseCase>()
    private val refreshExpense = mockk<RefreshExpenseUseCase>()
    private val refreshMembers = mockk<RefreshMembersUseCase>()
    private val deleteExpense = mockk<DeleteExpenseUseCase>()

    private fun createViewModel(
        expense: Expense? = expense(),
        members: List<GroupMember> = listOf(member("u-1", "Ana", GroupRole.OWNER), member("u-2", "Beto", GroupRole.MEMBER)),
        currentUserId: String = "u-1",
    ): ExpenseDetailViewModel {
        every { observeExpense("e-1") } returns flowOf(expense)
        every { observeMembers("g-1") } returns flowOf(members)
        coEvery { refreshExpense("e-1") } returns AppResult.Success(expense ?: expense())
        coEvery { refreshMembers("g-1") } returns AppResult.Success(Unit)
        return ExpenseDetailViewModel(
            observeExpense,
            observeMembers,
            refreshExpense,
            refreshMembers,
            deleteExpense,
            SavedStateHandle(
                mapOf(
                    ExpensesRoutes.ARG_GROUP_ID to "g-1",
                    ExpensesRoutes.ARG_EXPENSE_ID to "e-1",
                    ExpensesRoutes.ARG_CURRENT_USER_ID to currentUserId,
                ),
            ),
        )
    }

    @Test
    fun `loads the expense and stops the loading state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Cena", viewModel.state.value.expense?.description)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Ana", viewModel.state.value.memberName("u-1"))
        coVerify(exactly = 1) { refreshExpense("e-1") }
        coVerify(exactly = 1) { refreshMembers("g-1") }
    }

    @Test
    fun `the creator can edit their own expense`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(currentUserId = "u-1")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.canEdit())
    }

    @Test
    fun `an admin of the group can edit someone else's expense`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(
            expense = expense(createdBy = "u-9"),
            members = listOf(member("u-2", "Bea", GroupRole.ADMIN), member("u-9", "Dani", GroupRole.MEMBER)),
            currentUserId = "u-2",
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.canEdit())
    }

    @Test
    fun `a plain member cannot edit someone else's expense`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(
            expense = expense(createdBy = "u-9"),
            members = listOf(member("u-2", "Bea", GroupRole.MEMBER), member("u-9", "Dani", GroupRole.MEMBER)),
            currentUserId = "u-2",
        )
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canEdit())
    }

    @Test
    fun `deleting confirms and marks the expense as deleted`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { deleteExpense("e-1") } returns AppResult.Success(Unit)

        viewModel.onEvent(ExpenseDetailEvent.DeleteDialogOpened)
        assertTrue(viewModel.state.value.deleteDialogVisible)

        viewModel.onEvent(ExpenseDetailEvent.DeleteConfirmed)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.deleted)
        assertFalse(viewModel.state.value.deleteDialogVisible)
        assertNull(viewModel.state.value.deleteError)
        coVerify(exactly = 1) { deleteExpense("e-1") }
    }

    @Test
    fun `a failing delete keeps the dialog and exposes the error`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { deleteExpense("e-1") } returns AppResult.Failure(AppError.Forbidden)

        viewModel.onEvent(ExpenseDetailEvent.DeleteDialogOpened)
        viewModel.onEvent(ExpenseDetailEvent.DeleteConfirmed)
        advanceUntilIdle()

        assertEquals(AppError.Forbidden, viewModel.state.value.deleteError)
        assertTrue(viewModel.state.value.deleteDialogVisible)
        assertFalse(viewModel.state.value.deleted)
        assertFalse(viewModel.state.value.isDeleting)
    }

    @Test
    fun `dismissing hides the dialog without deleting`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEvent(ExpenseDetailEvent.DeleteDialogOpened)

        viewModel.onEvent(ExpenseDetailEvent.DeleteDialogDismissed)

        assertFalse(viewModel.state.value.deleteDialogVisible)
        coVerify(exactly = 0) { deleteExpense(any()) }
    }

    @Test
    fun `without a cached expense there is nothing to edit`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(expense = null)
        advanceUntilIdle()

        assertNull(viewModel.state.value.expense)
        assertFalse(viewModel.state.value.canEdit())
    }

    private fun expense(createdBy: String = "u-1") = Expense(
        id = "e-1",
        groupId = "g-1",
        description = "Cena",
        amountMinor = 4250,
        currency = "EUR",
        paidByUserId = "u-1",
        splitType = SplitType.EQUAL,
        expenseDate = "2026-02-01",
        createdBy = createdBy,
        version = 1,
    )

    private fun member(
        userId: String,
        displayName: String,
        role: GroupRole,
    ) = GroupMember(
        userId = userId,
        displayName = displayName,
        email = "$userId@example.com",
        role = role,
        status = MemberStatus.ACTIVE,
    )
}
