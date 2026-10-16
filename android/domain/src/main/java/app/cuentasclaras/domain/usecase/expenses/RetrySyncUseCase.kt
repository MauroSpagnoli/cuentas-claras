package app.cuentasclaras.domain.usecase.expenses

import app.cuentasclaras.domain.repository.ExpenseRepository
import javax.inject.Inject

/** Devuelve al outbox las operaciones fallidas de un grupo y relanza el drenado. */
class RetrySyncUseCase @Inject constructor(
    private val repository: ExpenseRepository,
) {
    suspend operator fun invoke(groupId: String): Unit = repository.retrySync(groupId)
}
