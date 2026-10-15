package app.cuentasclaras.core.network.dto

import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * Gasto tal como lo devuelve la API. `amountMinor` va en unidades mínimas de la
 * moneda, `expenseDate` es una fecha ISO y `version` el contador de concurrencia
 * optimista.
 */
@Serializable
data class ExpenseDto(
    val id: String = "",
    val groupId: String = "",
    val description: String = "",
    val amountMinor: Long = 0,
    val currency: String = "EUR",
    val paidByUserId: String = "",
    val splitType: String = "EQUAL",
    val category: String? = null,
    val expenseDate: String = "",
    val notes: String? = null,
    val createdBy: String = "",
    val createdAt: String? = null,
    val version: Long = 0,
    val shares: List<ExpenseShareDto> = emptyList(),
)

/** Parte resuelta de un gasto, con el importe final y el parámetro original. */
@Serializable
data class ExpenseShareDto(
    val userId: String = "",
    val amountMinor: Long = 0,
    @Serializable(with = BigDecimalSerializer::class) val percentage: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class) val weight: BigDecimal? = null,
)

/**
 * Parte de un gasto en una petición. El campo usado depende del tipo de
 * reparto: `amountMinor` para `EXACT`, `percentage` para `PERCENTAGE` y `weight`
 * para `SHARES`; en `EQUAL` solo viaja `userId`.
 */
@Serializable
data class ExpenseShareInputDto(
    val userId: String,
    val amountMinor: Long? = null,
    @Serializable(with = BigDecimalSerializer::class) val percentage: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class) val weight: BigDecimal? = null,
)

/**
 * Alta de gasto. La moneda no se envía: el backend usa la del grupo y rechaza
 * cualquier otra.
 */
@Serializable
data class CreateExpenseRequestDto(
    val description: String,
    val amountMinor: Long,
    val paidByUserId: String,
    val splitType: String,
    val expenseDate: String,
    val shares: List<ExpenseShareInputDto>,
    val category: String? = null,
    val notes: String? = null,
)

/**
 * Edición de gasto. Se envía el estado completo (el backend ignora los nulos) y
 * la categoría y las notas van como cadena vacía cuando se quieren borrar.
 */
@Serializable
data class UpdateExpenseRequestDto(
    val description: String,
    val amountMinor: Long,
    val paidByUserId: String,
    val splitType: String,
    val expenseDate: String,
    val shares: List<ExpenseShareInputDto>,
    val category: String,
    val notes: String,
    val version: Long,
)
