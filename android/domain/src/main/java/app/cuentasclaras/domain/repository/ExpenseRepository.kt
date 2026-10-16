package app.cuentasclaras.domain.repository

import androidx.paging.PagingData
import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.model.ExpenseDraft
import app.cuentasclaras.domain.model.SyncFailure
import app.cuentasclaras.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de gastos de un grupo.
 *
 * Room es la fuente de verdad: [observeExpenses] y [observeExpense] emiten desde
 * la caché local y la red solo la rellena (paginación con `Paging 3` +
 * `RemoteMediator`).
 *
 * Las altas, ediciones y bajas son **offline-first**: si hay red se aplican en
 * el servidor y se cachea la respuesta; si no la hay, el cambio se refleja de
 * forma optimista en Room y se encola en el outbox para sincronizarlo más tarde
 * (sin duplicados, gracias a la `Idempotency-Key`). El gasto afectado queda con
 * [Expense.pendingOperation] hasta que el servidor lo confirma.
 */
interface ExpenseRepository {

    /** Listado paginado de los gastos del grupo, servido desde la caché local. */
    fun observeExpenses(groupId: String): Flow<PagingData<Expense>>

    /** Observa un gasto concreto (con su reparto) desde la caché local. */
    fun observeExpense(expenseId: String): Flow<Expense?>

    /** Recarga el gasto desde la red y actualiza la caché. */
    suspend fun refreshExpense(expenseId: String): AppResult<Expense>

    /**
     * Alta de gasto. Con red, espera la respuesta del servidor; sin red, la
     * aplica de forma optimista y la encola en el outbox.
     */
    suspend fun createExpense(groupId: String, draft: ExpenseDraft): AppResult<Expense>

    /**
     * Edición de gasto. Con red, aplica el `PATCH`; sin red, actualiza la caché
     * de forma optimista y encola la operación.
     */
    suspend fun updateExpense(expenseId: String, draft: ExpenseDraft): AppResult<Expense>

    /**
     * Baja de gasto. Con red, la confirma el servidor; sin red, la quita de la
     * caché de forma optimista y encola la operación.
     */
    suspend fun deleteExpense(expenseId: String): AppResult<Unit>

    /**
     * Fallos de sincronización definitivos de un grupo (no se reintentan solos),
     * para que la UI avise y ofrezca reintentar.
     */
    fun observeSyncFailures(groupId: String): Flow<List<SyncFailure>>

    /** Devuelve las operaciones fallidas a la cola y relanza la sincronización. */
    suspend fun retrySync(groupId: String)
}
