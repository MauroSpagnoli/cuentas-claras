package app.cuentasclaras.domain.usecase.groups

import androidx.paging.PagingData
import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observa el listado paginado de grupos del usuario. */
class ObserveGroupsUseCase @Inject constructor(
    private val repository: GroupRepository,
) {
    operator fun invoke(): Flow<PagingData<Group>> = repository.observeGroups()
}
