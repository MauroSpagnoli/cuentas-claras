package app.cuentasclaras.domain.usecase.groups

import app.cuentasclaras.domain.repository.GroupRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/** Recarga desde la red la lista de miembros del grupo. */
class RefreshMembersUseCase @Inject constructor(
    private val repository: GroupRepository,
) {
    suspend operator fun invoke(groupId: String): AppResult<Unit> = repository.refreshMembers(groupId)
}
