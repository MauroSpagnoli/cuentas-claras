package app.cuentasclaras.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Gasto cacheado de un grupo. `amountMinor` está en unidades mínimas de la
 * moneda, `expenseDate` es una fecha ISO (clave del cursor del backend junto al
 * `id`) y `version` el contador de concurrencia optimista.
 *
 * El índice `(groupId, expenseDate)` sirve al listado paginado, que siempre
 * filtra por grupo y ordena por fecha descendente.
 */
@Entity(
    tableName = "expenses",
    indices = [Index(value = ["groupId", "expenseDate"])],
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val description: String,
    val amountMinor: Long,
    val currency: String,
    val paidByUserId: String,
    val splitType: String,
    val category: String?,
    val expenseDate: String,
    val notes: String?,
    val createdBy: String,
    val createdAt: String?,
    val version: Long,
)
