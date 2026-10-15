package app.cuentasclaras.feature.expenses.detail

import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.GroupRole

/**
 * Estado del detalle de un gasto: el gasto con su reparto, los miembros del
 * grupo (para resolver nombres) y el diálogo de borrado.
 */
data class ExpenseDetailUiState(
    val expense: Expense? = null,
    val members: List<GroupMember> = emptyList(),
    val currentUserId: String = "",
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val deleteDialogVisible: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteError: AppError? = null,
    val deleted: Boolean = false,
) {

    /**
     * El backend deja editar y borrar al creador del gasto y a los gestores del
     * grupo (propietario y administradores).
     */
    fun canEdit(): Boolean {
        val expense = expense ?: return false
        if (expense.createdBy == currentUserId) return true
        val role = members.firstOrNull { it.userId == currentUserId }?.role
        return role == GroupRole.OWNER || role == GroupRole.ADMIN
    }

    fun memberName(userId: String): String? =
        members.firstOrNull { it.userId == userId }?.displayName
}

sealed interface ExpenseDetailEvent {
    data object DeleteDialogOpened : ExpenseDetailEvent
    data object DeleteDialogDismissed : ExpenseDetailEvent
    data object DeleteConfirmed : ExpenseDetailEvent
}
