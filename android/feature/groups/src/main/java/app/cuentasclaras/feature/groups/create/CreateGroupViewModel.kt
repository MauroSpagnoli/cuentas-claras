package app.cuentasclaras.feature.groups.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.usecase.groups.CreateGroupUseCase
import app.cuentasclaras.domain.validation.GroupField
import app.cuentasclaras.domain.validation.GroupValidator
import app.cuentasclaras.domain.validation.ValidationIssue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel del formulario de creación de grupo. Valida en local antes de
 * llamar al caso de uso y, al crear, deja el id para que la UI navegue.
 */
@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val createGroup: CreateGroupUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateGroupUiState())
    val state: StateFlow<CreateGroupUiState> = _state.asStateFlow()

    fun onEvent(event: CreateGroupEvent) {
        when (event) {
            is CreateGroupEvent.NameChanged -> _state.update {
                it.copy(name = event.name, fieldIssues = it.fieldIssues - GroupField.NAME, error = null)
            }
            is CreateGroupEvent.DescriptionChanged -> _state.update {
                it.copy(description = event.description, fieldIssues = it.fieldIssues - GroupField.DESCRIPTION)
            }
            is CreateGroupEvent.CurrencyChanged -> _state.update {
                it.copy(currency = event.currency, fieldIssues = it.fieldIssues - GroupField.CURRENCY)
            }
            CreateGroupEvent.Submitted -> submit()
            CreateGroupEvent.CreatedGroupHandled -> _state.update { it.copy(createdGroupId = null) }
        }
    }

    private fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        val issues = validationIssues(current)
        if (issues.isNotEmpty()) {
            _state.update { it.copy(fieldIssues = issues, error = null) }
            return
        }

        _state.update { it.copy(isSubmitting = true, fieldIssues = emptyMap(), error = null) }
        viewModelScope.launch {
            when (val result = createGroup(current.name, current.description, current.currency)) {
                is AppResult.Success -> _state.update {
                    it.copy(isSubmitting = false, createdGroupId = result.value.id)
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isSubmitting = false, error = result.error)
                }
            }
        }
    }

    private fun validationIssues(state: CreateGroupUiState): Map<GroupField, ValidationIssue> =
        buildMap {
            GroupValidator.validateName(state.name)?.let { put(GroupField.NAME, it) }
            GroupValidator.validateDescription(state.description)?.let { put(GroupField.DESCRIPTION, it) }
            GroupValidator.validateCurrency(state.currency)?.let { put(GroupField.CURRENCY, it) }
        }
}
