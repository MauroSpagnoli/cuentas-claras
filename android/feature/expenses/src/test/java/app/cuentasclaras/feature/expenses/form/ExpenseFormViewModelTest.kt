package app.cuentasclaras.feature.expenses.form

import androidx.lifecycle.SavedStateHandle
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.model.ExpenseDraft
import app.cuentasclaras.domain.model.ExpenseShare
import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.GroupRole
import app.cuentasclaras.domain.model.MemberStatus
import app.cuentasclaras.domain.model.SplitType
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.usecase.expenses.CreateExpenseUseCase
import app.cuentasclaras.domain.usecase.expenses.ObserveExpenseUseCase
import app.cuentasclaras.domain.usecase.expenses.RefreshExpenseUseCase
import app.cuentasclaras.domain.usecase.expenses.UpdateExpenseUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveGroupUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveMembersUseCase
import app.cuentasclaras.domain.usecase.groups.RefreshMembersUseCase
import app.cuentasclaras.domain.validation.ExpenseField
import app.cuentasclaras.domain.validation.ExpenseIssue
import app.cuentasclaras.feature.expenses.ExpensesRoutes
import app.cuentasclaras.feature.expenses.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExpenseFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val observeExpense = mockk<ObserveExpenseUseCase>()
    private val observeGroup = mockk<ObserveGroupUseCase>()
    private val observeMembers = mockk<ObserveMembersUseCase>()
    private val refreshExpense = mockk<RefreshExpenseUseCase>()
    private val refreshMembers = mockk<RefreshMembersUseCase>()
    private val createExpense = mockk<CreateExpenseUseCase>()
    private val updateExpense = mockk<UpdateExpenseUseCase>()

    private val ana = member("u-1", "Ana")
    private val beto = member("u-2", "Beto")

    private fun createViewModel(
        expenseId: String? = null,
        currentUserId: String = "u-1",
        members: List<GroupMember> = listOf(ana, beto),
        cachedExpense: Expense? = expense(),
        refreshFailure: AppError? = null,
    ): ExpenseFormViewModel {
        every { observeGroup("g-1") } returns flowOf(Group(id = "g-1", name = "Piso", currency = "EUR"))
        every { observeMembers("g-1") } returns flowOf(members)
        coEvery { refreshMembers("g-1") } returns AppResult.Success(Unit)
        every { observeExpense("e-1") } returns flowOf(cachedExpense)
        coEvery { refreshExpense("e-1") } returns (
            refreshFailure?.let { AppResult.Failure(it) }
                ?: AppResult.Success(cachedExpense ?: expense())
            )
        coEvery { createExpense(any(), any()) } returns AppResult.Success(expense())
        coEvery { updateExpense(any(), any()) } returns AppResult.Success(expense())

        val args = buildMap {
            put(ExpensesRoutes.ARG_GROUP_ID, "g-1")
            put(ExpensesRoutes.ARG_CURRENT_USER_ID, currentUserId)
            if (expenseId != null) put(ExpensesRoutes.ARG_EXPENSE_ID, expenseId)
        }
        return ExpenseFormViewModel(
            observeExpense,
            observeGroup,
            observeMembers,
            refreshExpense,
            refreshMembers,
            createExpense,
            updateExpense,
            SavedStateHandle(args),
        )
    }

    @Test
    fun `a new expense starts empty with today and not loading`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isEditing)
        assertFalse(state.isLoading)
        assertEquals("", state.description)
        assertTrue(state.expenseDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertEquals("EUR", state.currency)
    }

    @Test
    fun `only active members become participants`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(
            members = listOf(ana, beto.copy(status = MemberStatus.INVITED)),
        )
        advanceUntilIdle()

        assertEquals(listOf("u-1"), viewModel.state.value.members.map { it.userId })
        assertEquals(setOf("u-1"), viewModel.state.value.participants.keys)
    }

    @Test
    fun `the payer defaults to the current user and every participant starts selected`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel(currentUserId = "u-2")
            advanceUntilIdle()

            assertEquals("u-2", viewModel.state.value.paidByUserId)
            assertTrue(viewModel.state.value.participants.values.all { it.selected })
            assertEquals(listOf("u-1", "u-2"), viewModel.state.value.selectedParticipantIds)
        }

    @Test
    fun `the payer falls back to the first member when the current user is not in the group`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel(currentUserId = "u-99")
            advanceUntilIdle()

            assertEquals("u-1", viewModel.state.value.paidByUserId)
        }

    @Test
    fun `changing the amount previews the equal split`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(ExpenseFormEvent.AmountChanged("10,00"))

        assertEquals(mapOf("u-1" to 500L, "u-2" to 500L), viewModel.state.value.preview)
    }

    @Test
    fun `submitting creates the expense with the resolved split`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val draft = slot<ExpenseDraft>()

        viewModel.onEvent(ExpenseFormEvent.DescriptionChanged("Cena"))
        viewModel.onEvent(ExpenseFormEvent.AmountChanged("10,00"))
        viewModel.onEvent(ExpenseFormEvent.Submitted)
        advanceUntilIdle()

        coVerify(exactly = 1) { createExpense("g-1", capture(draft)) }
        assertEquals(1000L, draft.captured.amountMinor)
        assertEquals(SplitType.EQUAL, draft.captured.splitType)
        assertEquals(2, draft.captured.shares.size)
        assertTrue(viewModel.state.value.saved)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun `a blank description blocks the submit`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(ExpenseFormEvent.AmountChanged("10,00"))
        viewModel.onEvent(ExpenseFormEvent.Submitted)
        advanceUntilIdle()

        assertEquals(ExpenseIssue.Required, viewModel.state.value.fieldIssues[ExpenseField.DESCRIPTION])
        coVerify(exactly = 0) { createExpense(any(), any()) }
    }

    @Test
    fun `an invalid amount blocks the submit`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(ExpenseFormEvent.DescriptionChanged("Cena"))
        viewModel.onEvent(ExpenseFormEvent.AmountChanged("0"))
        viewModel.onEvent(ExpenseFormEvent.Submitted)
        advanceUntilIdle()

        assertEquals(ExpenseIssue.AmountNotPositive, viewModel.state.value.fieldIssues[ExpenseField.AMOUNT])
        coVerify(exactly = 0) { createExpense(any(), any()) }
    }

    @Test
    fun `without participants there is no split`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(ExpenseFormEvent.DescriptionChanged("Cena"))
        viewModel.onEvent(ExpenseFormEvent.AmountChanged("10,00"))
        viewModel.onEvent(ExpenseFormEvent.ParticipantToggled("u-1"))
        viewModel.onEvent(ExpenseFormEvent.ParticipantToggled("u-2"))
        viewModel.onEvent(ExpenseFormEvent.Submitted)
        advanceUntilIdle()

        assertEquals(ExpenseIssue.NoParticipants, viewModel.state.value.splitIssue)
        coVerify(exactly = 0) { createExpense(any(), any()) }
    }

    @Test
    fun `an exact split that does not add up blocks the submit`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(ExpenseFormEvent.DescriptionChanged("Cena"))
        viewModel.onEvent(ExpenseFormEvent.AmountChanged("10,00"))
        viewModel.onEvent(ExpenseFormEvent.SplitTypeChanged(SplitType.EXACT))
        viewModel.onEvent(ExpenseFormEvent.ParticipantAmountChanged("u-1", "4,00"))
        viewModel.onEvent(ExpenseFormEvent.ParticipantAmountChanged("u-2", "5,00"))
        viewModel.onEvent(ExpenseFormEvent.Submitted)
        advanceUntilIdle()

        assertEquals(ExpenseIssue.ExactSumMismatch(1000, 900), viewModel.state.value.splitIssue)
        coVerify(exactly = 0) { createExpense(any(), any()) }
    }

    @Test
    fun `a percentage split sends the percentages`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val draft = slot<ExpenseDraft>()

        viewModel.onEvent(ExpenseFormEvent.DescriptionChanged("Cena"))
        viewModel.onEvent(ExpenseFormEvent.AmountChanged("10,00"))
        viewModel.onEvent(ExpenseFormEvent.SplitTypeChanged(SplitType.PERCENTAGE))
        viewModel.onEvent(ExpenseFormEvent.ParticipantPercentageChanged("u-1", "50"))
        viewModel.onEvent(ExpenseFormEvent.ParticipantPercentageChanged("u-2", "50"))
        viewModel.onEvent(ExpenseFormEvent.Submitted)
        advanceUntilIdle()

        coVerify(exactly = 1) { createExpense("g-1", capture(draft)) }
        assertEquals(SplitType.PERCENTAGE, draft.captured.splitType)
        assertEquals(
            listOf(BigDecimal("50"), BigDecimal("50")),
            draft.captured.shares.map { it.percentage },
        )
        assertEquals(2, draft.captured.shares.size)
    }

    @Test
    fun `a failed create exposes the error`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { createExpense(any(), any()) } returns
            AppResult.Failure(AppError.Conflict("Version desactualizada"))

        viewModel.onEvent(ExpenseFormEvent.DescriptionChanged("Cena"))
        viewModel.onEvent(ExpenseFormEvent.AmountChanged("10,00"))
        viewModel.onEvent(ExpenseFormEvent.Submitted)
        advanceUntilIdle()

        assertEquals(AppError.Conflict("Version desactualizada"), viewModel.state.value.error)
        assertFalse(viewModel.state.value.saved)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun `editing prefills the form from the cached expense`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(
            expenseId = "e-1",
            cachedExpense = expense(
                description = "Cena de equipo",
                amountMinor = 4250,
                splitType = SplitType.PERCENTAGE,
                date = "2026-01-15",
                version = 3,
                shares = listOf(
                    ExpenseShare("u-1", 2125, percentage = BigDecimal("50")),
                    ExpenseShare("u-2", 2125, percentage = BigDecimal("50")),
                ),
            ),
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isEditing)
        assertFalse(state.isLoading)
        assertEquals("Cena de equipo", state.description)
        assertEquals("42.5", state.amount)
        assertEquals("2026-01-15", state.expenseDate)
        assertEquals("u-1", state.paidByUserId)
        assertEquals(SplitType.PERCENTAGE, state.splitType)
        assertEquals(3L, state.version)
        assertTrue(state.participants.values.all { it.selected })
        assertEquals(mapOf("u-1" to 2125L, "u-2" to 2125L), state.preview)
    }

    @Test
    fun `editing sends the known version`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(
            expenseId = "e-1",
            cachedExpense = expense(
                version = 5,
                shares = listOf(ExpenseShare("u-1", 4250)),
            ),
        )
        advanceUntilIdle()
        val draft = slot<ExpenseDraft>()

        viewModel.onEvent(ExpenseFormEvent.Submitted)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateExpense("e-1", capture(draft)) }
        coVerify(exactly = 0) { createExpense(any(), any()) }
        assertEquals(5L, draft.captured.version)
    }

    @Test
    fun `editing without a cached expense surfaces the refresh error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel(
                expenseId = "e-1",
                cachedExpense = null,
                refreshFailure = AppError.NotFound,
            )
            advanceUntilIdle()

            assertEquals(AppError.NotFound, viewModel.state.value.error)
            assertFalse(viewModel.state.value.isLoading)
        }

    @Test
    fun `editing keeps only the shares it knows as participants`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(
            expenseId = "e-1",
            cachedExpense = expense(shares = listOf(ExpenseShare("u-1", 4250))),
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.participant("u-1").selected)
        assertFalse(viewModel.state.value.participant("u-2").selected)
        assertNull(viewModel.state.value.splitIssue)
    }

    private fun expense(
        description: String = "Cena",
        amountMinor: Long = 4250,
        splitType: SplitType = SplitType.EQUAL,
        date: String = "2026-02-01",
        version: Long = 1,
        shares: List<ExpenseShare> = emptyList(),
    ) = Expense(
        id = "e-1",
        groupId = "g-1",
        description = description,
        amountMinor = amountMinor,
        currency = "EUR",
        paidByUserId = "u-1",
        splitType = splitType,
        expenseDate = date,
        createdBy = "u-1",
        version = version,
        shares = shares,
    )

    private fun member(userId: String, displayName: String) = GroupMember(
        userId = userId,
        displayName = displayName,
        email = "$userId@example.com",
        role = GroupRole.MEMBER,
        status = MemberStatus.ACTIVE,
    )
}
