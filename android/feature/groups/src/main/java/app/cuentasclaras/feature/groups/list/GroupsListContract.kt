package app.cuentasclaras.feature.groups.list

import app.cuentasclaras.domain.error.AppError

/**
 * Estado del listado de grupos. El propio listado paginado no vive aquí: se
 * expone aparte como `LazyPagingItems` (`GroupsListViewModel.groups`).
 */
data class GroupsListUiState(
    val joinDialogVisible: Boolean = false,
    val joinCode: String = "",
    val isJoining: Boolean = false,
    val joinError: AppError? = null,
    /** Grupo recién aceptado; la UI navega a su detalle y lo consume. */
    val joinedGroupId: String? = null,
) {
    val canSubmitJoin: Boolean get() = !isJoining && joinCode.isNotBlank()
}

/** Eventos que la pantalla envía al ViewModel. */
sealed interface GroupsListEvent {
    data object JoinDialogOpened : GroupsListEvent
    data object JoinDialogDismissed : GroupsListEvent
    data class JoinCodeChanged(val code: String) : GroupsListEvent
    data object JoinSubmitted : GroupsListEvent
    data object JoinedGroupHandled : GroupsListEvent
}
