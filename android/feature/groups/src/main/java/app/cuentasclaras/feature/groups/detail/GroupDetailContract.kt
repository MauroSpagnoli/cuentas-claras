package app.cuentasclaras.feature.groups.detail

import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.GroupRole
import app.cuentasclaras.domain.model.Invite
import app.cuentasclaras.domain.validation.ValidationIssue

/**
 * Estado del detalle de un grupo: la ficha, sus miembros y los diálogos de
 * añadir miembro / invitar.
 */
data class GroupDetailUiState(
    val group: Group? = null,
    val members: List<GroupMember> = emptyList(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val addMemberVisible: Boolean = false,
    val addMemberEmail: String = "",
    val addMemberIssue: ValidationIssue? = null,
    val addMemberError: AppError? = null,
    val isAddingMember: Boolean = false,
    /** Usuario con una acción de gestión en curso (rol o expulsión). */
    val busyUserId: String? = null,
    val memberActionError: AppError? = null,
    val inviteVisible: Boolean = false,
    val isCreatingInvite: Boolean = false,
    val invite: Invite? = null,
    val inviteError: AppError? = null,
) {
    /** El usuario actual puede gestionar miembros si es propietario o admin. */
    fun canManage(currentUserId: String): Boolean = members.any {
        it.userId == currentUserId && (it.role == GroupRole.OWNER || it.role == GroupRole.ADMIN)
    }
}

sealed interface GroupDetailEvent {
    data object Refreshed : GroupDetailEvent
    data object AddMemberOpened : GroupDetailEvent
    data object AddMemberDismissed : GroupDetailEvent
    data class AddMemberEmailChanged(val email: String) : GroupDetailEvent
    data object AddMemberSubmitted : GroupDetailEvent
    data class MemberRoleChangeRequested(val userId: String, val role: GroupRole) : GroupDetailEvent
    data class MemberRemovalRequested(val userId: String) : GroupDetailEvent
    data object MemberActionErrorDismissed : GroupDetailEvent
    data object InviteRequested : GroupDetailEvent
    data object InviteDismissed : GroupDetailEvent
}
