package app.cuentasclaras.feature.balances.form

import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.validation.SettlementField
import app.cuentasclaras.domain.validation.SettlementIssue

/**
 * Estado del formulario para registrar un pago.
 *
 * [fromUserId] y [toUserId] llegan prerrellenados desde la sugerencia del plan de
 * liquidación; cuando el usuario registra un pago a mano parten vacíos y se
 * presupone como pagador al usuario con la sesión abierta.
 */
data class SettlementFormUiState(
    val isLoading: Boolean = true,
    val currency: String = "",
    val members: List<GroupMember> = emptyList(),
    val fromUserId: String? = null,
    val toUserId: String? = null,
    val amount: String = "",
    val settledOn: String = "",
    val note: String = "",
    val fieldIssues: Map<SettlementField, SettlementIssue> = emptyMap(),
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    val saved: Boolean = false,
) {

    fun memberName(userId: String): String? =
        members.firstOrNull { it.userId == userId }?.displayName

    /** Hacen falta al menos dos miembros para que un pago tenga sentido. */
    val canSubmit: Boolean get() = !isSubmitting && !isLoading && members.size >= 2
}

sealed interface SettlementFormEvent {

    /** Cambia quién paga. */
    data class PayerChanged(val userId: String) : SettlementFormEvent

    /** Cambia quién cobra. */
    data class PayeeChanged(val userId: String) : SettlementFormEvent

    data class AmountChanged(val value: String) : SettlementFormEvent

    data class DateChanged(val isoDate: String) : SettlementFormEvent

    data class NoteChanged(val value: String) : SettlementFormEvent

    data object Submitted : SettlementFormEvent
}
