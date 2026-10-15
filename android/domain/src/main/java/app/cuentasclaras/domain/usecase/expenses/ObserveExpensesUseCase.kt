package app.cuentasclaras.domain.usecase.expenses

import androidx.paging.PagingData
import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observa el listado paginado de gastos de un grupo (caché local). */
class ObserveExpensesUseCase @Inject constructor(
    private val repository: ExpenseRepository,
) {
    operator fun invoke(groupId: String): Flow<PagingData<Expense>> = repository.observeExpenses(groupId)
}
