package app.cuentasclaras.domain.usecase.expenses

import app.cuentasclaras.domain.model.SyncFailure
import app.cuentasclaras.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observa los fallos de sincronización definitivos de un grupo, para que la UI
 * avise al usuario (por ejemplo, un conflicto de versión al editar).
 */
class ObserveSyncFailuresUseCase @Inject constructor(
    private val repository: ExpenseRepository,
) {
    operator fun invoke(groupId: String): Flow<List<SyncFailure>> =
        repository.observeSyncFailures(groupId)
}
