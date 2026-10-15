package app.cuentasclaras.feature.groups.create

import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.validation.GroupField
import app.cuentasclaras.domain.validation.ValidationIssue

/** Estado del formulario de creación de grupo. */
data class CreateGroupUiState(
    val name: String = "",
    val description: String = "",
    val currency: String = "EUR",
    val fieldIssues: Map<GroupField, ValidationIssue> = emptyMap(),
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    /** Id del grupo creado; la UI vuelve al listado y lo consume. */
    val createdGroupId: String? = null,
) {
    val canSubmit: Boolean get() = !isSubmitting && name.isNotBlank()
}

sealed interface CreateGroupEvent {
    data class NameChanged(val name: String) : CreateGroupEvent
    data class DescriptionChanged(val description: String) : CreateGroupEvent
    data class CurrencyChanged(val currency: String) : CreateGroupEvent
    data object Submitted : CreateGroupEvent
    data object CreatedGroupHandled : CreateGroupEvent
}
