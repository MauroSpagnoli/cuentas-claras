package app.cuentasclaras.domain.usecase.groups

import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observa un grupo concreto de la caché local. */
class ObserveGroupUseCase @Inject constructor(
    private val repository: GroupRepository,
) {
    operator fun invoke(groupId: String): Flow<Group?> = repository.observeGroup(groupId)
}
