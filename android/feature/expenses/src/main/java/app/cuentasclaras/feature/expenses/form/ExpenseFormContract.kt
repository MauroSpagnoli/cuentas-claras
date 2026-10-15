package app.cuentasclaras.feature.expenses.form

import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.SplitType
import app.cuentasclaras.domain.validation.ExpenseField
import app.cuentasclaras.domain.validation.ExpenseIssue

/** Lo que el usuario edita de un participante del reparto. */
data class ParticipantInput(
    val selected: Boolean = false,
    val amount: String = "",
    val percentage: String = "",
    val weight: String = "",
)

/**
 * Estado del formulario de alta/edición de un gasto.
 *
 * [preview] es el reparto calculado en local (importe en unidades mínimas por
 * participante) para que el usuario vea el resultado antes de guardarlo; el
 * cálculo definitivo lo hace el backend.
 */
data class ExpenseFormUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = true,
    /** Versión conocida del gasto; se envía en la edición para detectar conflictos. */
    val version: Long? = null,
    val currency: String = "",
    val members: List<GroupMember> = emptyList(),
    val description: String = "",
    val amount: String = "",
    val expenseDate: String = "",
    val category: String = "",
    val notes: String = "",
    val paidByUserId: String? = null,
    val splitType: SplitType = SplitType.EQUAL,
    val participants: Map<String, ParticipantInput> = emptyMap(),
    val fieldIssues: Map<ExpenseField, ExpenseIssue> = emptyMap(),
    val splitIssue: ExpenseIssue? = null,
    val preview: Map<String, Long> = emptyMap(),
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    val saved: Boolean = false,
) {

    /** Identificadores de los participantes marcados, en el orden del grupo. */
    val selectedParticipantIds: List<String>
        get() = members.map { it.userId }.filter { participants[it]?.selected == true }

    fun participant(userId: String): ParticipantInput = participants[userId] ?: ParticipantInput()

    fun memberName(userId: String): String? =
        members.firstOrNull { it.userId == userId }?.displayName

    val canSubmit: Boolean
        get() = !isSubmitting && !isLoading
}

sealed interface ExpenseFormEvent {

    data class DescriptionChanged(val value: String) : ExpenseFormEvent

    data class AmountChanged(val value: String) : ExpenseFormEvent

    data class DateChanged(val isoDate: String) : ExpenseFormEvent

    data class CategoryChanged(val value: String) : ExpenseFormEvent

    data class NotesChanged(val value: String) : ExpenseFormEvent

    data class PayerChanged(val userId: String) : ExpenseFormEvent

    data class SplitTypeChanged(val splitType: SplitType) : ExpenseFormEvent

    data class ParticipantToggled(val userId: String) : ExpenseFormEvent

    data class ParticipantAmountChanged(val userId: String, val value: String) : ExpenseFormEvent

    data class ParticipantPercentageChanged(val userId: String, val value: String) : ExpenseFormEvent

    data class ParticipantWeightChanged(val userId: String, val value: String) : ExpenseFormEvent

    data object Submitted : ExpenseFormEvent
}
