package app.cuentasclaras.domain.usecase.groups

import app.cuentasclaras.domain.repository.GroupRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/** Saca a un miembro del grupo. */
class RemoveMemberUseCase @Inject constructor(
    private val repository: GroupRepository,
) {
    suspend operator fun invoke(groupId: String, userId: String): AppResult<Unit> =
        repository.removeMember(groupId, userId)
}
