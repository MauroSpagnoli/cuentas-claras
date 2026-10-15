package app.cuentasclaras.core.database.entity

import androidx.room.Entity

/**
 * Parte resuelta de un gasto. La clave primaria es compuesta (`expenseId`,
 * `userId`).
 *
 * `percentage` y `weight` se guardan como texto plano de `BigDecimal` para no
 * perder precisión: son los parámetros originales del reparto y el backend los
 * compara de forma exacta (una suma de porcentajes debe dar 100).
 */
@Entity(tableName = "expense_shares", primaryKeys = ["expenseId", "userId"])
data class ExpenseShareEntity(
    val expenseId: String,
    val userId: String,
    val amountMinor: Long,
    val percentage: String?,
    val weight: String?,
)
