package app.cuentasclaras.domain.usecase.groups

import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.repository.GroupRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/** Crea un grupo normalizando nombre, descripción y moneda. */
class CreateGroupUseCase @Inject constructor(
    private val repository: GroupRepository,
) {
    suspend operator fun invoke(
        name: String,
        description: String,
        currency: String,
    ): AppResult<Group> = repository.createGroup(
        name = name.trim(),
        description = description.trim().takeIf { it.isNotEmpty() },
        currency = currency.trim().uppercase(),
    )
}
