package app.cuentasclaras.feature.groups.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.usecase.groups.AddMemberUseCase
import app.cuentasclaras.domain.usecase.groups.ChangeMemberRoleUseCase
import app.cuentasclaras.domain.usecase.groups.CreateInviteUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveGroupUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveMembersUseCase
import app.cuentasclaras.domain.usecase.groups.RefreshGroupUseCase
import app.cuentasclaras.domain.usecase.groups.RefreshMembersUseCase
import app.cuentasclaras.domain.usecase.groups.RemoveMemberUseCase
import app.cuentasclaras.domain.validation.GroupValidator
import app.cuentasclaras.feature.groups.GroupsRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel del detalle de grupo.
 *
 * Room es la fuente de verdad: el grupo y sus miembros se observan desde la
 * caché y [refresh] los rellena desde la red. Las acciones de gestión (añadir,
 * cambiar rol, expulsar) escriben en el backend y la caché se actualiza sola.
 */
@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val observeGroup: ObserveGroupUseCase,
    private val observeMembers: ObserveMembersUseCase,
    private val refreshGroup: RefreshGroupUseCase,
    private val refreshMembers: RefreshMembersUseCase,
    private val addMember: AddMemberUseCase,
    private val changeMemberRole: ChangeMemberRoleUseCase,
    private val removeMember: RemoveMemberUseCase,
    private val createInvite: CreateInviteUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val groupId: String = requireNotNull(savedStateHandle.get<String>(GroupsRoutes.ARG_GROUP_ID)) {
        "Falta el argumento ${GroupsRoutes.ARG_GROUP_ID} en la ruta de detalle de grupo"
    }

    private val _state = MutableStateFlow(GroupDetailUiState())
    val state: StateFlow<GroupDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeGroup(groupId).collect { group ->
                _state.update { current -> current.copy(group = group, isLoading = current.isLoading && group == null) }
            }
        }
        viewModelScope.launch {
            observeMembers(groupId).collect { members -> _state.update { it.copy(members = members) } }
        }
        refresh()
    }

    fun onEvent(event: GroupDetailEvent) {
        when (event) {
            GroupDetailEvent.Refreshed -> refresh()

            GroupDetailEvent.AddMemberOpened -> _state.update {
                it.copy(addMemberVisible = true, addMemberEmail = "", addMemberIssue = null, addMemberError = null)
            }
            GroupDetailEvent.AddMemberDismissed -> _state.update {
                it.copy(addMemberVisible = false, addMemberEmail = "", addMemberIssue = null, addMemberError = null)
            }
            is GroupDetailEvent.AddMemberEmailChanged -> _state.update {
                it.copy(addMemberEmail = event.email, addMemberIssue = null, addMemberError = null)
            }
            GroupDetailEvent.AddMemberSubmitted -> submitAddMember()

            is GroupDetailEvent.MemberRoleChangeRequested -> runMemberAction(event.userId) {
                changeMemberRole(groupId, event.userId, event.role)
            }
            is GroupDetailEvent.MemberRemovalRequested -> runMemberAction(event.userId) {
                removeMember(groupId, event.userId)
            }
            GroupDetailEvent.MemberActionErrorDismissed -> _state.update { it.copy(memberActionError = null) }

            GroupDetailEvent.InviteRequested -> createInvitation()
            GroupDetailEvent.InviteDismissed -> _state.update {
                it.copy(inviteVisible = false, invite = null, inviteError = null)
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.group == null, error = null) }
            val groupResult = refreshGroup(groupId)
            val membersResult = refreshMembers(groupId)
            val failure: AppError? = (groupResult as? AppResult.Failure)?.error
                ?: (membersResult as? AppResult.Failure)?.error
            _state.update { it.copy(isLoading = false, error = failure) }
        }
    }

    private fun submitAddMember() {
        val email = _state.value.addMemberEmail
        val issue = GroupValidator.validateEmail(email)
        if (issue != null) {
            _state.update { it.copy(addMemberIssue = issue) }
            return
        }

        _state.update { it.copy(isAddingMember = true, addMemberError = null) }
        viewModelScope.launch {
            when (val result = addMember(groupId, email)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        isAddingMember = false,
                        addMemberVisible = false,
                        addMemberEmail = "",
                        addMemberIssue = null,
                    )
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isAddingMember = false, addMemberError = result.error)
                }
            }
        }
    }

    private fun runMemberAction(userId: String, action: suspend () -> AppResult<*>) {
        if (_state.value.busyUserId != null) return

        _state.update { it.copy(busyUserId = userId, memberActionError = null) }
        viewModelScope.launch {
            when (val result = action()) {
                is AppResult.Success -> _state.update { it.copy(busyUserId = null) }
                is AppResult.Failure -> _state.update {
                    it.copy(busyUserId = null, memberActionError = result.error)
                }
            }
        }
    }

    private fun createInvitation() {
        _state.update { it.copy(inviteVisible = true, isCreatingInvite = true, invite = null, inviteError = null) }
        viewModelScope.launch {
            when (val result = createInvite(groupId)) {
                is AppResult.Success -> _state.update {
                    it.copy(isCreatingInvite = false, invite = result.value)
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isCreatingInvite = false, inviteError = result.error)
                }
            }
        }
    }
}
