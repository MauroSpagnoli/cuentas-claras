package app.cuentasclaras.feature.balances.form

import androidx.lifecycle.SavedStateHandle
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.GroupRole
import app.cuentasclaras.domain.model.MemberStatus
import app.cuentasclaras.domain.model.Settlement
import app.cuentasclaras.domain.model.SettlementDraft
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.usecase.balances.RegisterSettlementUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveGroupUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveMembersUseCase
import app.cuentasclaras.domain.usecase.groups.RefreshMembersUseCase
import app.cuentasclaras.domain.validation.SettlementField
import app.cuentasclaras.domain.validation.SettlementIssue
import app.cuentasclaras.domain.validation.SettlementValidator
import app.cuentasclaras.feature.balances.BalancesRoutes
import app.cuentasclaras.feature.balances.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettlementFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val observeGroup = mockk<ObserveGroupUseCase>()
    private val observeMembers = mockk<ObserveMembersUseCase>()
    private val refreshMembers = mockk<RefreshMembersUseCase>()
    private val registerSettlement = mockk<RegisterSettlementUseCase>()

    private val ana = member("u-1", "Ana", GroupRole.OWNER)
    private val beto = member("u-2", "Beto", GroupRole.MEMBER)

    private fun createViewModel(
        currentUserId: String = "u-1",
        fromUserId: String? = null,
        toUserId: String? = null,
        amountMinor: Long? = null,
        members: List<GroupMember> = listOf(ana, beto),
    ): SettlementFormViewModel {
        every { observeGroup("g-1") } returns flowOf(Group(id = "g-1", name = "Piso", currency = "EUR"))
        every { observeMembers("g-1") } returns flowOf(members)
        coEvery { refreshMembers("g-1") } returns AppResult.Success(Unit)
        coEvery { registerSettlement(any(), any()) } returns AppResult.Success(settlement())

        val args = buildMap<String, Any> {
            put(BalancesRoutes.ARG_GROUP_ID, "g-1")
            put(BalancesRoutes.ARG_CURRENT_USER_ID, currentUserId)
            if (fromUserId != null) put(BalancesRoutes.ARG_FROM_USER_ID, fromUserId)
            if (toUserId != null) put(BalancesRoutes.ARG_TO_USER_ID, toUserId)
            if (amountMinor != null) put(BalancesRoutes.ARG_AMOUNT_MINOR, amountMinor)
        }
        return SettlementFormViewModel(
            observeGroup,
            observeMembers,
            refreshMembers,
            registerSettlement,
            SavedStateHandle(args),
        )
    }

    @Test
    fun `defaults the payer to the current user and the payee to another member`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel(currentUserId = "u-2")
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("u-2", state.fromUserId)
            assertEquals("u-1", state.toUserId)
            assertEquals("EUR", state.currency)
            assertFalse(state.isLoading)
            assertEquals("Ana", state.memberName("u-1"))
        }

    @Test
    fun `falls back to the first member when the current user is not in the group`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel(currentUserId = "u-99")
            advanceUntilIdle()

            assertEquals("u-1", viewModel.state.value.fromUserId)
            assertEquals("u-2", viewModel.state.value.toUserId)
        }

    @Test
    fun `prefills the parties and the amount from the liquidation step`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel(
                fromUserId = "u-2",
                toUserId = "u-1",
                amountMinor = 1250,
            )
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("u-2", state.fromUserId)
            assertEquals("u-1", state.toUserId)
            assertEquals("12.5", state.amount)
        }

    @Test
    fun `ignores members that are not active`() = runTest(mainDispatcherRule.dispatcher) {
        every { observeGroup("g-1") } returns flowOf(Group(id = "g-1", name = "Piso", currency = "EUR"))
        every { observeMembers("g-1") } returns flowOf(
            listOf(member("u-1", "Ana", GroupRole.MEMBER), invited("u-2", "Beto")),
        )
        coEvery { refreshMembers("g-1") } returns AppResult.Success(Unit)

        val viewModel = SettlementFormViewModel(
            observeGroup,
            observeMembers,
            refreshMembers,
            registerSettlement,
            SavedStateHandle(
                mapOf(
                    BalancesRoutes.ARG_GROUP_ID to "g-1",
                    BalancesRoutes.ARG_CURRENT_USER_ID to "u-1",
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("u-1"), viewModel.state.value.members.map { it.userId })
        assertNull(viewModel.state.value.toUserId)
        assertFalse(viewModel.state.value.canSubmit)
    }

    @Test
    fun `needs the group id in the route`() {
        assertThrows(IllegalArgumentException::class.java) {
            SettlementFormViewModel(
                observeGroup,
                observeMembers,
                refreshMembers,
                registerSettlement,
                SavedStateHandle(),
            )
        }
    }

    @Test
    fun `registers the payment with the prefilled amount`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(
            fromUserId = "u-2",
            toUserId = "u-1",
            amountMinor = 1250,
        )
        advanceUntilIdle()
        val draft = slot<SettlementDraft>()

        viewModel.onEvent(SettlementFormEvent.NoteChanged("Bizum"))
        viewModel.onEvent(SettlementFormEvent.Submitted)
        advanceUntilIdle()

        coVerify(exactly = 1) { registerSettlement("g-1", capture(draft)) }
        assertEquals("u-2", draft.captured.fromUserId)
        assertEquals("u-1", draft.captured.toUserId)
        assertEquals(1250L, draft.captured.amountMinor)
        assertEquals("Bizum", draft.captured.note)
        assertTrue(draft.captured.settledOn.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertTrue(viewModel.state.value.saved)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun `sends no note when it is blank`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(fromUserId = "u-1", toUserId = "u-2", amountMinor = 500)
        advanceUntilIdle()
        val draft = slot<SettlementDraft>()

        viewModel.onEvent(SettlementFormEvent.Submitted)
        advanceUntilIdle()

        coVerify(exactly = 1) { registerSettlement("g-1", capture(draft)) }
        assertNull(draft.captured.note)
    }

    @Test
    fun `a same-party payment blocks the submit`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // El pagador por defecto es u-1; si el cobro también apunta a u-1, no vale.
        viewModel.onEvent(SettlementFormEvent.PayeeChanged("u-1"))
        viewModel.onEvent(SettlementFormEvent.AmountChanged("10"))
        viewModel.onEvent(SettlementFormEvent.Submitted)
        advanceUntilIdle()

        assertEquals(
            SettlementIssue.SameParty,
            viewModel.state.value.fieldIssues[SettlementField.PARTIES],
        )
        coVerify(exactly = 0) { registerSettlement(any(), any()) }
    }

    @Test
    fun `a non-positive amount blocks the submit`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(SettlementFormEvent.AmountChanged("0"))
        viewModel.onEvent(SettlementFormEvent.Submitted)
        advanceUntilIdle()

        assertEquals(
            SettlementIssue.AmountNotPositive,
            viewModel.state.value.fieldIssues[SettlementField.AMOUNT],
        )
        coVerify(exactly = 0) { registerSettlement(any(), any()) }
    }

    @Test
    fun `an impossible date blocks the submit`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(SettlementFormEvent.AmountChanged("10"))
        viewModel.onEvent(SettlementFormEvent.DateChanged("2026-13-01"))
        viewModel.onEvent(SettlementFormEvent.Submitted)
        advanceUntilIdle()

        assertEquals(
            SettlementIssue.InvalidDate,
            viewModel.state.value.fieldIssues[SettlementField.DATE],
        )
        coVerify(exactly = 0) { registerSettlement(any(), any()) }
    }

    @Test
    fun `a too long note blocks the submit`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(SettlementFormEvent.AmountChanged("10"))
        viewModel.onEvent(SettlementFormEvent.NoteChanged("x".repeat(SettlementValidator.NOTE_MAX_LENGTH + 1)))
        viewModel.onEvent(SettlementFormEvent.Submitted)
        advanceUntilIdle()

        assertEquals(
            SettlementIssue.TooLong(SettlementValidator.NOTE_MAX_LENGTH),
            viewModel.state.value.fieldIssues[SettlementField.NOTE],
        )
        coVerify(exactly = 0) { registerSettlement(any(), any()) }
    }

    @Test
    fun `editing a field clears its validation issue`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(SettlementFormEvent.Submitted)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.fieldIssues.containsKey(SettlementField.AMOUNT))

        viewModel.onEvent(SettlementFormEvent.AmountChanged("10"))

        assertFalse(viewModel.state.value.fieldIssues.containsKey(SettlementField.AMOUNT))
    }

    @Test
    fun `a failed payment exposes the error`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(fromUserId = "u-1", toUserId = "u-2", amountMinor = 500)
        advanceUntilIdle()
        coEvery { registerSettlement(any(), any()) } returns
            AppResult.Failure(AppError.Conflict("Ya existe"))

        viewModel.onEvent(SettlementFormEvent.Submitted)
        advanceUntilIdle()

        assertEquals(AppError.Conflict("Ya existe"), viewModel.state.value.error)
        assertFalse(viewModel.state.value.saved)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    private fun settlement() = Settlement(
        id = "s-1",
        groupId = "g-1",
        fromUserId = "u-1",
        toUserId = "u-2",
        amountMinor = 500,
        currency = "EUR",
        settledOn = "2026-02-01",
    )

    private fun member(userId: String, displayName: String, role: GroupRole) = GroupMember(
        userId = userId,
        displayName = displayName,
        email = "$userId@example.com",
        role = role,
        status = MemberStatus.ACTIVE,
    )

    private fun invited(userId: String, displayName: String) = GroupMember(
        userId = userId,
        displayName = displayName,
        email = "$userId@example.com",
        role = GroupRole.MEMBER,
        status = MemberStatus.INVITED,
    )
}
