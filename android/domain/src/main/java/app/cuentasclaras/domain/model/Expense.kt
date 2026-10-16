package app.cuentasclaras.domain.model

import java.math.BigDecimal

/**
 * Forma de repartir un gasto entre sus participantes. Los valores coinciden con
 * los del backend (`SplitType`) y con los que viajan por la API.
 */
enum class SplitType {
    EQUAL,
    EXACT,
    PERCENTAGE,
    SHARES;

    companion object {
        /** Convierte el valor del backend; ante uno desconocido degrada a [EQUAL]. */
        fun fromApi(raw: String?): SplitType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: EQUAL
    }
}

/**
 * Parte resuelta de un gasto: [amountMinor] es el importe final en unidades
 * mínimas de la moneda. [percentage] y [weight] reflejan el parámetro original
 * del reparto (nulos cuando no aplican a ese tipo).
 */
data class ExpenseShare(
    val userId: String,
    val amountMinor: Long,
    val percentage: BigDecimal? = null,
    val weight: BigDecimal? = null,
)

/**
 * Gasto de un grupo.
 *
 * [amountMinor] está en unidades mínimas de la moneda (p. ej. céntimos) y
 * [expenseDate] es una fecha ISO (`yyyy-MM-dd`). `version` es el contador de
 * concurrencia optimista del backend: si se envía en una edición y no coincide,
 * la petición falla con 409.
 *
 * [pendingOperation] es la mutación optimista aún sin sincronizar (`CREATE` o
 * `UPDATE`), o `null` si el gasto ya está confirmado por el servidor.
 */
data class Expense(
    val id: String,
    val groupId: String,
    val description: String,
    val amountMinor: Long,
    val currency: String,
    val paidByUserId: String,
    val splitType: SplitType,
    val expenseDate: String,
    val createdBy: String,
    val category: String? = null,
    val notes: String? = null,
    val createdAt: String? = null,
    val version: Long = 0,
    val shares: List<ExpenseShare> = emptyList(),
    val pendingOperation: OutboxOperation? = null,
)
