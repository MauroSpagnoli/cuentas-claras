package app.cuentasclaras.domain.usecase.expenses

import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.repository.ExpenseRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/** Recarga desde la red un gasto concreto y actualiza la caché. */
class RefreshExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository,
) {
    suspend operator fun invoke(expenseId: String): AppResult<Expense> = repository.refreshExpense(expenseId)
}
