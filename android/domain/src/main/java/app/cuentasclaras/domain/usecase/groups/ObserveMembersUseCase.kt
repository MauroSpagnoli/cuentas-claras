package app.cuentasclaras.domain.usecase.groups

import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observa los miembros de un grupo (caché local). */
class ObserveMembersUseCase @Inject constructor(
    private val repository: GroupRepository,
) {
    operator fun invoke(groupId: String): Flow<List<GroupMember>> = repository.observeMembers(groupId)
}
