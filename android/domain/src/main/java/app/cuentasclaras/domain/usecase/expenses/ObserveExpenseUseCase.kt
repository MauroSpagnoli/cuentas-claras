package app.cuentasclaras.domain.usecase.expenses

import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observa un gasto concreto con su reparto (caché local). */
class ObserveExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository,
) {
    operator fun invoke(expenseId: String): Flow<Expense?> = repository.observeExpense(expenseId)
}
