package app.cuentasclaras.domain.usecase.groups

import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.repository.GroupRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/** Recarga el detalle del grupo desde la red. */
class RefreshGroupUseCase @Inject constructor(
    private val repository: GroupRepository,
) {
    suspend operator fun invoke(groupId: String): AppResult<Group> = repository.refreshGroup(groupId)
}
