package app.cuentasclaras.domain.usecase.expenses

import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.model.ExpenseDraft
import app.cuentasclaras.domain.repository.ExpenseRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/** Crea un gasto en un grupo normalizando sus textos. */
class CreateExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository,
) {
    suspend operator fun invoke(groupId: String, draft: ExpenseDraft): AppResult<Expense> =
        repository.createExpense(groupId, draft.normalized())
}
