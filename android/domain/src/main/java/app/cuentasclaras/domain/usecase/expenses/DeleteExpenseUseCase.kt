package app.cuentasclaras.domain.usecase.expenses

import app.cuentasclaras.domain.repository.ExpenseRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/** Borra un gasto: baja lógica en el backend y baja de la caché local. */
class DeleteExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository,
) {
    suspend operator fun invoke(expenseId: String): AppResult<Unit> = repository.deleteExpense(expenseId)
}
