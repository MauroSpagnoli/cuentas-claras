package app.cuentasclaras.feature.groups.list

import androidx.paging.PagingData
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.usecase.groups.AcceptInviteUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveGroupsUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GroupsListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val observeGroups = mockk<ObserveGroupsUseCase>()
    private val acceptInvite = mockk<AcceptInviteUseCase>()

    private fun createViewModel(): GroupsListViewModel {
        every { observeGroups() } returns flowOf(PagingData.empty())
        return GroupsListViewModel(observeGroups, acceptInvite)
    }

    @Test
    fun `starts with the join dialog closed`() {
        val viewModel = createViewModel()

        assertEquals(false, viewModel.state.value.joinDialogVisible)
        assertEquals(false, viewModel.state.value.canSubmitJoin)
    }

    @Test
    fun `opening and dismissing the dialog resets the code`() {
        val viewModel = createViewModel()

        viewModel.onEvent(GroupsListEvent.JoinDialogOpened)
        assertEquals(true, viewModel.state.value.joinDialogVisible)

        viewModel.onEvent(GroupsListEvent.JoinCodeChanged("ABC123"))
        viewModel.onEvent(GroupsListEvent.JoinDialogDismissed)

        assertEquals(false, viewModel.state.value.joinDialogVisible)
        assertEquals("", viewModel.state.value.joinCode)
    }

    @Test
    fun `blank code does nothing and skips the use case`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(GroupsListEvent.JoinSubmitted)
        advanceUntilIdle()

        coVerify(exactly = 0) { acceptInvite(any()) }
        assertEquals(null, viewModel.state.value.joinError)
    }

    @Test
    fun `valid code joins the group and exposes its id`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { acceptInvite("ABCDEFGHIJ") } returns AppResult.Success(
            Group(id = "g-9", name = "Viaje"),
        )
        val viewModel = createViewModel()

        viewModel.onEvent(GroupsListEvent.JoinDialogOpened)
        viewModel.onEvent(GroupsListEvent.JoinCodeChanged("ABCDEFGHIJ"))
        viewModel.onEvent(GroupsListEvent.JoinSubmitted)
        advanceUntilIdle()

        assertEquals("g-9", viewModel.state.value.joinedGroupId)
        assertEquals(false, viewModel.state.value.joinDialogVisible)
        assertEquals(false, viewModel.state.value.isJoining)
    }

    @Test
    fun `conflict keeps the dialog open with the error`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { acceptInvite(any()) } returns AppResult.Failure(
            AppError.Conflict("Ya perteneces a este grupo"),
        )
        val viewModel = createViewModel()

        viewModel.onEvent(GroupsListEvent.JoinDialogOpened)
        viewModel.onEvent(GroupsListEvent.JoinCodeChanged("ABCDEFGHIJ"))
        viewModel.onEvent(GroupsListEvent.JoinSubmitted)
        advanceUntilIdle()

        assertEquals(AppError.Conflict("Ya perteneces a este grupo"), viewModel.state.value.joinError)
        assertEquals(true, viewModel.state.value.joinDialogVisible)
        assertEquals(null, viewModel.state.value.joinedGroupId)
    }

    @Test
    fun `handling the joined group clears the navigation signal`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { acceptInvite(any()) } returns AppResult.Success(Group(id = "g-9", name = "Viaje"))
        val viewModel = createViewModel()

        viewModel.onEvent(GroupsListEvent.JoinDialogOpened)
        viewModel.onEvent(GroupsListEvent.JoinCodeChanged("ABCDEFGHIJ"))
        viewModel.onEvent(GroupsListEvent.JoinSubmitted)
        advanceUntilIdle()
        viewModel.onEvent(GroupsListEvent.JoinedGroupHandled)

        assertEquals(null, viewModel.state.value.joinedGroupId)
    }

    @Test
    fun `editing the code clears the previous error`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { acceptInvite(any()) } returns AppResult.Failure(AppError.NotFound)
        val viewModel = createViewModel()

        viewModel.onEvent(GroupsListEvent.JoinDialogOpened)
        viewModel.onEvent(GroupsListEvent.JoinCodeChanged("ABCDEFGHIJ"))
        viewModel.onEvent(GroupsListEvent.JoinSubmitted)
        advanceUntilIdle()
        assertEquals(AppError.NotFound, viewModel.state.value.joinError)

        viewModel.onEvent(GroupsListEvent.JoinCodeChanged("ABCDEFGHI"))

        assertEquals(null, viewModel.state.value.joinError)
    }
}
