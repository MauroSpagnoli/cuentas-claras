package app.cuentasclaras.feature.expenses.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.cuentasclaras.domain.model.Expense
import app.cuentasclaras.domain.usecase.expenses.ObserveExpensesUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveGroupUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveMembersUseCase
import app.cuentasclaras.domain.usecase.groups.RefreshGroupUseCase
import app.cuentasclaras.domain.usecase.groups.RefreshMembersUseCase
import app.cuentasclaras.feature.expenses.ExpensesRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel del listado de gastos de un grupo.
 *
 * El `PagingData` se cachea en el scope del ViewModel para que la lista
 * sobreviva a recomposiciones y a la navegación al detalle sin volver a lanzar
 * la sincronización remota. El nombre del grupo y los de sus miembros se leen
 * también de la caché.
 */
@HiltViewModel
class ExpensesListViewModel @Inject constructor(
    observeExpenses: ObserveExpensesUseCase,
    observeGroup: ObserveGroupUseCase,
    observeMembers: ObserveMembersUseCase,
    refreshGroup: RefreshGroupUseCase,
    refreshMembers: RefreshMembersUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Grupo cuyos gastos se listan; viaja en la ruta. */
    val groupId: String = requireNotNull(savedStateHandle.get<String>(ExpensesRoutes.ARG_GROUP_ID)) {
        "La ruta de gastos necesita el id del grupo"
    }

    val expenses: Flow<PagingData<Expense>> = observeExpenses(groupId).cachedIn(viewModelScope)

    private val _state = MutableStateFlow(ExpensesListUiState())
    val state: StateFlow<ExpensesListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeGroup(groupId).collect { group ->
                if (group != null) _state.update { it.copy(groupName = group.name) }
            }
        }
        viewModelScope.launch {
            observeMembers(groupId).collect { members ->
                _state.update { it.copy(members = members) }
            }
        }
        viewModelScope.launch {
            refreshGroup(groupId)
            refreshMembers(groupId)
        }
    }
}
