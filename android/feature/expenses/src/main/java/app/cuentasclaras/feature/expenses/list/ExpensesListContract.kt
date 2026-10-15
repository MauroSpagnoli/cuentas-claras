package app.cuentasclaras.feature.expenses.list

import app.cuentasclaras.domain.model.GroupMember

/**
 * Estado del listado de gastos de un grupo: su nombre (para el título) y los
 * miembros, que la UI usa para mostrar quién pagó cada gasto.
 */
data class ExpensesListUiState(
    val groupName: String? = null,
    val members: List<GroupMember> = emptyList(),
) {

    /** Nombre del pagador, o `null` mientras no se conozcan los miembros. */
    fun memberName(userId: String): String? =
        members.firstOrNull { it.userId == userId }?.displayName
}
