package app.cuentasclaras.domain.usecase.expenses

import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.model.ExpenseDraft
import app.cuentasclaras.domain.repository.ExpenseRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/**
 * Edita un gasto normalizando sus textos. El borrador lleva la versión conocida
 * para que el backend devuelva 409 si otro cliente lo modificó antes.
 */
class UpdateExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository,
) {
    suspend operator fun invoke(expenseId: String, draft: ExpenseDraft): AppResult<Expense> =
        repository.updateExpense(expenseId, draft.normalized())
}
