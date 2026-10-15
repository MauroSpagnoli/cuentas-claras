package app.cuentasclaras.domain.usecase.groups

import app.cuentasclaras.domain.model.Invite
import app.cuentasclaras.domain.repository.GroupRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/** Genera una invitación nueva para el grupo. */
class CreateInviteUseCase @Inject constructor(
    private val repository: GroupRepository,
) {
    suspend operator fun invoke(groupId: String): AppResult<Invite> = repository.createInvite(groupId)
}
