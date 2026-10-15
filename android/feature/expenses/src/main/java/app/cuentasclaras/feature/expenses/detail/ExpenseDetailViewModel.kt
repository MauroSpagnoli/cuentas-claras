package app.cuentasclaras.feature.expenses.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.usecase.expenses.DeleteExpenseUseCase
import app.cuentasclaras.domain.usecase.expenses.ObserveExpenseUseCase
import app.cuentasclaras.domain.usecase.expenses.RefreshExpenseUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveMembersUseCase
import app.cuentasclaras.domain.usecase.groups.RefreshMembersUseCase
import app.cuentasclaras.feature.expenses.ExpensesRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel del detalle de un gasto.
 *
 * Exige que el gasto esté en la caché (siempre llega desde el listado o tras
 * crearlo) y, además, lo recarga de la red para mostrar la versión más reciente
 * antes de editarlo: el `version` que se envíe en el `PATCH` tiene que coincidir.
 */
@HiltViewModel
class ExpenseDetailViewModel @Inject constructor(
    private val observeExpense: ObserveExpenseUseCase,
    observeMembers: ObserveMembersUseCase,
    private val refreshExpense: RefreshExpenseUseCase,
    private val refreshMembers: RefreshMembersUseCase,
    private val deleteExpense: DeleteExpenseUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val groupId: String = requireNotNull(savedStateHandle[ExpensesRoutes.ARG_GROUP_ID]) {
        "El detalle del gasto necesita el id del grupo"
    }

    val expenseId: String = requireNotNull(savedStateHandle[ExpensesRoutes.ARG_EXPENSE_ID]) {
        "El detalle del gasto necesita su id"
    }

    private val _state = MutableStateFlow(
        ExpenseDetailUiState(
            currentUserId = savedStateHandle.get<String>(ExpensesRoutes.ARG_CURRENT_USER_ID).orEmpty(),
        ),
    )
    val state: StateFlow<ExpenseDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeExpense(expenseId).collect { expense ->
                if (expense != null) {
                    _state.update { it.copy(expense = expense, isLoading = false) }
                }
            }
        }

        viewModelScope.launch {
            observeMembers(groupId).collect { members ->
                _state.update { it.copy(members = members) }
            }
        }

        viewModelScope.launch {
            refreshExpense(expenseId)
            refreshMembers(groupId)
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun onEvent(event: ExpenseDetailEvent) {
        when (event) {
            ExpenseDetailEvent.DeleteDialogOpened -> _state.update {
                it.copy(deleteDialogVisible = true, deleteError = null)
            }

            ExpenseDetailEvent.DeleteDialogDismissed -> _state.update {
                it.copy(deleteDialogVisible = false)
            }

            ExpenseDetailEvent.DeleteConfirmed -> delete()
        }
    }

    private fun delete() {
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, deleteError = null) }
            when (val result = deleteExpense(expenseId)) {
                is AppResult.Success -> _state.update {
                    it.copy(isDeleting = false, deleteDialogVisible = false, deleted = true)
                }

                is AppResult.Failure -> _state.update {
                    it.copy(isDeleting = false, deleteError = result.error)
                }
            }
        }
    }
}
