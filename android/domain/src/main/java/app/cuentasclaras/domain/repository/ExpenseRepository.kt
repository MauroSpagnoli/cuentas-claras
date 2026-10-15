package app.cuentasclaras.domain.repository

import androidx.paging.PagingData
import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.model.ExpenseDraft
import app.cuentasclaras.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de gastos de un grupo.
 *
 * Room es la fuente de verdad: [observeExpenses] y [observeExpense] emiten desde
 * la caché local y la red solo la rellena (paginación con `Paging 3` +
 * `RemoteMediator`). Las altas, ediciones y bajas llaman al backend y actualizan
 * la caché para que el cambio se vea al instante; el siguiente refresco vuelve a
 * traer del servidor el estado definitivo.
 */
interface ExpenseRepository {

    /** Listado paginado de los gastos del grupo, servido desde la caché local. */
    fun observeExpenses(groupId: String): Flow<PagingData<Expense>>

    /** Observa un gasto concreto (con su reparto) desde la caché local. */
    fun observeExpense(expenseId: String): Flow<Expense?>

    /** Recarga el gasto desde la red y actualiza la caché. */
    suspend fun refreshExpense(expenseId: String): AppResult<Expense>

    /** Crea un gasto en el grupo y lo cachea. */
    suspend fun createExpense(groupId: String, draft: ExpenseDraft): AppResult<Expense>

    /** Edita un gasto y actualiza la caché. */
    suspend fun updateExpense(expenseId: String, draft: ExpenseDraft): AppResult<Expense>

    /** Da de baja un gasto en el backend y lo quita de la caché. */
    suspend fun deleteExpense(expenseId: String): AppResult<Unit>
}
