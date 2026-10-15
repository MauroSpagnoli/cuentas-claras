package app.cuentasclaras.domain.model

import java.math.BigDecimal

/**
 * Parte de un gasto tal como la escribe el usuario. El campo que viaja a la API
 * depende del tipo de reparto: [amountMinor] para `EXACT`, [percentage] para
 * `PERCENTAGE` y [weight] para `SHARES`. En `EQUAL` solo importa [userId].
 */
data class ExpenseShareInput(
    val userId: String,
    val amountMinor: Long? = null,
    val percentage: BigDecimal? = null,
    val weight: BigDecimal? = null,
)

/** Datos de alta o edición de un gasto, ya resueltos por la capa de presentación. */
data class ExpenseDraft(
    val description: String,
    val amountMinor: Long,
    val paidByUserId: String,
    val splitType: SplitType,
    val expenseDate: String,
    val shares: List<ExpenseShareInput> = emptyList(),
    val category: String? = null,
    val notes: String? = null,
    /**
     * Versión conocida del gasto. En edición permite que el backend detecte que
     * otro cliente lo modificó antes (409).
     */
    val version: Long? = null,
)
