package app.cuentasclaras.feature.expenses.list

import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.SyncFailure

/**
 * Estado del listado de gastos de un grupo: su nombre (para el título), los
 * miembros (que la UI usa para mostrar quién pagó cada gasto) y los fallos de
 * sincronización pendientes de avisar al usuario.
 */
data class ExpensesListUiState(
    val groupName: String? = null,
    val members: List<GroupMember> = emptyList(),
    val syncFailures: List<SyncFailure> = emptyList(),
) {

    /** Nombre del pagador, o `null` mientras no se conozcan los miembros. */
    fun memberName(userId: String): String? =
        members.firstOrNull { it.userId == userId }?.displayName

    val hasSyncFailures: Boolean get() = syncFailures.isNotEmpty()
}
