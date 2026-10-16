package app.cuentasclaras.domain.model

/**
 * Pago registrado entre dos miembros de un grupo.
 *
 * [amountMinor] está en unidades mínimas de la moneda y [settledOn] es la fecha
 * ISO (`yyyy-MM-dd`) en la que se saldó. Un pago es la única forma de mover el
 * signo de un balance sin crear un gasto.
 */
data class Settlement(
    val id: String,
    val groupId: String,
    val fromUserId: String,
    val toUserId: String,
    val amountMinor: Long,
    val currency: String,
    val settledOn: String,
    val note: String? = null,
    val createdAt: String? = null,
)

/** Datos para registrar un pago, ya resueltos por la capa de presentación. */
data class SettlementDraft(
    val fromUserId: String,
    val toUserId: String,
    val amountMinor: Long,
    val settledOn: String,
    val note: String? = null,
) {

    /** Recorta los textos y descarta la nota vacía antes de enviar la petición. */
    fun normalized(): SettlementDraft = copy(
        fromUserId = fromUserId.trim(),
        toUserId = toUserId.trim(),
        settledOn = settledOn.trim(),
        note = note?.trim()?.takeIf { it.isNotEmpty() },
    )
}
