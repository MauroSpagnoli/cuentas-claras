package app.cuentasclaras.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.cuentasclaras.core.database.entity.ExpenseEntity
import app.cuentasclaras.core.database.entity.ExpenseShareEntity
import app.cuentasclaras.core.database.entity.ExpenseSyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    /**
     * Fuente de datos paginada de los gastos de un grupo, servida desde la
     * caché. El orden replica el del backend (`expenseDate` desc, `id` desc).
     */
    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY expenseDate DESC, id DESC")
    fun pagingSource(groupId: String): PagingSource<Int, ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    fun observeById(expenseId: String): Flow<ExpenseEntity?>

    @Query("SELECT * FROM expense_shares WHERE expenseId = :expenseId ORDER BY userId ASC")
    fun observeShares(expenseId: String): Flow<List<ExpenseShareEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE groupId = :groupId")
    suspend fun clearExpenses(groupId: String)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpense(expenseId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShares(shares: List<ExpenseShareEntity>)

    @Query("DELETE FROM expense_shares WHERE expenseId = :expenseId")
    suspend fun clearShares(expenseId: String)

    @Query("DELETE FROM expense_shares WHERE expenseId IN (SELECT id FROM expenses WHERE groupId = :groupId)")
    suspend fun clearSharesOfGroup(groupId: String)

    @Query("SELECT * FROM expense_sync_state WHERE groupId = :groupId")
    suspend fun syncState(groupId: String): ExpenseSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: ExpenseSyncStateEntity)
}
