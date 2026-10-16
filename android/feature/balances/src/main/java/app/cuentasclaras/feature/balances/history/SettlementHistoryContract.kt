package app.cuentasclaras.feature.balances.history

import app.cuentasclaras.domain.model.GroupMember

/**
 * Estado de la historia de pagos: los miembros del grupo sirven para resolver los
 * nombres de `from`/`to` de cada pago.
 */
data class SettlementHistoryUiState(
    val members: List<GroupMember> = emptyList(),
) {

    /** Nombre del miembro, o `null` mientras no se conozcan los miembros. */
    fun memberName(userId: String): String? =
        members.firstOrNull { it.userId == userId }?.displayName
}
