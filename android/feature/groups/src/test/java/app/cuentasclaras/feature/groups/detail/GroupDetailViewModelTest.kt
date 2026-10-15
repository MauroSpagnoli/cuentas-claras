package app.cuentasclaras.feature.groups.detail

import androidx.lifecycle.SavedStateHandle
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.GroupRole
import app.cuentasclaras.domain.model.Invite
import app.cuentasclaras.domain.model.MemberStatus
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.usecase.groups.AddMemberUseCase
import app.cuentasclaras.domain.usecase.groups.ChangeMemberRoleUseCase
import app.cuentasclaras.domain.usecase.groups.CreateInviteUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveGroupUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveMembersUseCase
import app.cuentasclaras.domain.usecase.groups.RefreshGroupUseCase
import app.cuentasclaras.domain.usecase.groups.RefreshMembersUseCase
import app.cuentasclaras.domain.usecase.groups.RemoveMemberUseCase
import app.cuentasclaras.domain.validation.ValidationIssue
import app.cuentasclaras.feature.groups.GroupsRoutes
import app.cuentasclaras.feature.groups.MainDispatcherRule
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GroupDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val observeGroup = mockk<ObserveGroupUseCase>()
    private val observeMembers = mockk<ObserveMembersUseCase>()
    private val refreshGroup = mockk<RefreshGroupUseCase>()
    private val refreshMembers = mockk<RefreshMembersUseCase>()
    private val addMember = mockk<AddMemberUseCase>()
    private val changeMemberRole = mockk<ChangeMemberRoleUseCase>()
    private val removeMember = mockk<RemoveMemberUseCase>()
    private val createInvite = mockk<CreateInviteUseCase>()

    @Before
    fun setUp() {
        every { observeGroup(GROUP_ID) } returns flowOf(group)
        every { observeMembers(GROUP_ID) } returns flowOf(listOf(ownerMember, plainMember))
        coEvery { refreshGroup(GROUP_ID) } returns AppResult.Success(group)
        coEvery { refreshMembers(GROUP_ID) } returns AppResult.Success(Unit)
    }

    private fun createViewModel() = GroupDetailViewModel(
        observeGroup = observeGroup,
        observeMembers = observeMembers,
        refreshGroup = refreshGroup,
        refreshMembers = refreshMembers,
        addMember = addMember,
        changeMemberRole = changeMemberRole,
        removeMember = removeMember,
        createInvite = createInvite,
        savedStateHandle = SavedStateHandle(mapOf(GroupsRoutes.ARG_GROUP_ID to GROUP_ID)),
    )

    @Test
    fun `reads the group id from the saved state`() {
        assertEquals(GROUP_ID, createViewModel().groupId)
    }

    @Test
    fun `publishes the cached group and members`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        advanceUntilIdle()

        assertEquals("Piso", viewModel.state.value.group?.name)
        assertEquals(2, viewModel.state.value.members.size)
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `surfaces the refresh failure once loading finishes`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { refreshMembers(GROUP_ID) } returns AppResult.Failure(AppError.Network)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(AppError.Network, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `only owner or admin can manage members`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.canManage(OWNER_ID))
        assertFalse(viewModel.state.value.canManage(MEMBER_ID))
        assertFalse(viewModel.state.value.canManage("outro"))
    }

    @Test
    fun `blank email surfaces a required issue and skips the use case`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(GroupDetailEvent.AddMemberOpened)
            viewModel.onEvent(GroupDetailEvent.AddMemberSubmitted)
            advanceUntilIdle()

            assertEquals(ValidationIssue.Required, viewModel.state.value.addMemberIssue)
            coVerify(exactly = 0) { addMember(any(), any()) }
        }

    @Test
    fun `adds a member and closes the dialog`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { addMember(GROUP_ID, "nuevo@example.com") } returns AppResult.Success(plainMember)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(GroupDetailEvent.AddMemberOpened)
        viewModel.onEvent(GroupDetailEvent.AddMemberEmailChanged("nuevo@example.com"))
        viewModel.onEvent(GroupDetailEvent.AddMemberSubmitted)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.addMemberVisible)
        assertFalse(viewModel.state.value.isAddingMember)
        assertNull(viewModel.state.value.addMemberError)
    }

    @Test
    fun `forbidden add member keeps the dialog open with the error`() =
        runTest(mainDispatcherRule.dispatcher) {
            coEvery { addMember(GROUP_ID, any()) } returns AppResult.Failure(AppError.Forbidden)
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(GroupDetailEvent.AddMemberOpened)
            viewModel.onEvent(GroupDetailEvent.AddMemberEmailChanged("nuevo@example.com"))
            viewModel.onEvent(GroupDetailEvent.AddMemberSubmitted)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.addMemberVisible)
            assertEquals(AppError.Forbidden, viewModel.state.value.addMemberError)
        }

    @Test
    fun `changing a role reports the failure and clears the busy flag`() =
        runTest(mainDispatcherRule.dispatcher) {
            coEvery { changeMemberRole(GROUP_ID, MEMBER_ID, any()) } returns
                AppResult.Failure(AppError.Conflict("No puedes cambiar ese rol"))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(
                GroupDetailEvent.MemberRoleChangeRequested(MEMBER_ID, GroupRole.ADMIN),
            )
            advanceUntilIdle()

            assertNull(viewModel.state.value.busyUserId)
            assertEquals(AppError.Conflict("No puedes cambiar ese rol"), viewModel.state.value.memberActionError)
        }

    @Test
    fun `removes a member through the use case`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { removeMember(GROUP_ID, MEMBER_ID) } returns AppResult.Success(Unit)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(GroupDetailEvent.MemberRemovalRequested(MEMBER_ID))
        advanceUntilIdle()

        coVerify(exactly = 1) { removeMember(GROUP_ID, MEMBER_ID) }
        assertNull(viewModel.state.value.busyUserId)
        assertNull(viewModel.state.value.memberActionError)
    }

    @Test
    fun `creates an invitation when the dialog is requested`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { createInvite(GROUP_ID) } returns AppResult.Success(
            Invite(code = "ABCDEFGHIJ", url = "https://cuentas-claras.app/invite/ABCDEFGHIJ"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(GroupDetailEvent.InviteRequested)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.inviteVisible)
        assertEquals("ABCDEFGHIJ", viewModel.state.value.invite?.code)
        assertFalse(viewModel.state.value.isCreatingInvite)
    }

    @Test
    fun `dismissing the invitation clears its state`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { createInvite(GROUP_ID) } returns AppResult.Success(Invite("ABCDEFGHIJ", "url"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(GroupDetailEvent.InviteRequested)
        advanceUntilIdle()
        viewModel.onEvent(GroupDetailEvent.InviteDismissed)

        assertFalse(viewModel.state.value.inviteVisible)
        assertNull(viewModel.state.value.invite)
    }

    private companion object {
        const val GROUP_ID = "g-1"
        const val OWNER_ID = "u-owner"
        const val MEMBER_ID = "u-member"

        val group = Group(id = GROUP_ID, name = "Piso", currency = "EUR", memberCount = 2)
        val ownerMember = GroupMember(
            userId = OWNER_ID,
            displayName = "Ana",
            email = "ana@example.com",
            role = GroupRole.OWNER,
            status = MemberStatus.ACTIVE,
        )
        val plainMember = GroupMember(
            userId = MEMBER_ID,
            email = "luis@example.com",
            displayName = "Luis",
            role = GroupRole.MEMBER,
            status = MemberStatus.ACTIVE,
        )
    }
}
