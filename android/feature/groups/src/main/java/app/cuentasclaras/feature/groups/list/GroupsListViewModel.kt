package app.cuentasclaras.feature.groups.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.usecase.groups.AcceptInviteUseCase
import app.cuentasclaras.domain.usecase.groups.ObserveGroupsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel del listado de grupos.
 *
 * El `PagingData` se cachea en el scope del ViewModel para que la lista
 * sobreviva a recomposiciones y a la navegación al detalle sin volver a lanzar
 * la sincronización remota.
 */
@HiltViewModel
class GroupsListViewModel @Inject constructor(
    observeGroups: ObserveGroupsUseCase,
    private val acceptInvite: AcceptInviteUseCase,
) : ViewModel() {

    /** Grupos servidos desde la caché local (Room) y paginados con Paging 3. */
    val groups: Flow<PagingData<Group>> = observeGroups().cachedIn(viewModelScope)

    private val _state = MutableStateFlow(GroupsListUiState())
    val state: StateFlow<GroupsListUiState> = _state.asStateFlow()

    fun onEvent(event: GroupsListEvent) {
        when (event) {
            GroupsListEvent.JoinDialogOpened ->
                update { it.copy(joinDialogVisible = true, joinError = null) }

            GroupsListEvent.JoinDialogDismissed -> update {
                it.copy(joinDialogVisible = false, joinCode = "", joinError = null)
            }

            is GroupsListEvent.JoinCodeChanged -> update {
                it.copy(joinCode = event.code, joinError = null)
            }

            GroupsListEvent.JoinSubmitted -> join()

            GroupsListEvent.JoinedGroupHandled -> update { it.copy(joinedGroupId = null) }
        }
    }

    private fun update(transform: (GroupsListUiState) -> GroupsListUiState) {
        _state.update(transform)
    }

    /** El botón del diálogo ya está deshabilitado sin código, así que aquí solo se ignora. */
    private fun join() {
        val code = _state.value.joinCode.trim()
        if (code.isEmpty()) return

        _state.update { it.copy(isJoining = true, joinError = null) }
        viewModelScope.launch {
            when (val result = acceptInvite(code)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        isJoining = false,
                        joinDialogVisible = false,
                        joinCode = "",
                        joinedGroupId = result.value.id,
                    )
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isJoining = false, joinError = result.error)
                }
            }
        }
    }
}
