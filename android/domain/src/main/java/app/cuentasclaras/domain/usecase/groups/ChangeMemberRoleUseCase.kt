package app.cuentasclaras.domain.usecase.groups

import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.GroupRole
import app.cuentasclaras.domain.repository.GroupRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/** Cambia el rol de un miembro del grupo. */
class ChangeMemberRoleUseCase @Inject constructor(
    private val repository: GroupRepository,
) {
    suspend operator fun invoke(
        groupId: String,
        userId: String,
        role: GroupRole,
    ): AppResult<GroupMember> = repository.changeMemberRole(groupId, userId, role)
}
