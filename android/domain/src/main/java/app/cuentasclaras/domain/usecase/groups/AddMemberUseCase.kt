package app.cuentasclaras.domain.usecase.groups

import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.GroupRole
import app.cuentasclaras.domain.repository.GroupRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/** Añade un miembro por email. Sin rol explícito el backend aplica `MEMBER`. */
class AddMemberUseCase @Inject constructor(
    private val repository: GroupRepository,
) {
    suspend operator fun invoke(
        groupId: String,
        email: String,
        role: GroupRole? = null,
    ): AppResult<GroupMember> = repository.addMember(
        groupId = groupId,
        email = email.trim().lowercase(),
        role = role,
    )
}
