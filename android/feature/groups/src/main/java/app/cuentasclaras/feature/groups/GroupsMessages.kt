package app.cuentasclaras.feature.groups

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.cuentasclaras.domain.error.ApiException
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.GroupRole
import app.cuentasclaras.domain.model.MemberStatus
import app.cuentasclaras.domain.validation.ValidationIssue

/**
 * Traducciones de los tipos de dominio a textos localizados. Todo el mapeo
 * vive aquí para que las pantallas no repitan el `when`.
 */
@Composable
fun AppError.toMessage(): String = when (this) {
    AppError.Network -> stringResource(R.string.groups_error_network)
    AppError.Unauthorized -> stringResource(R.string.groups_error_unauthorized)
    AppError.Forbidden -> stringResource(R.string.groups_error_forbidden)
    AppError.NotFound -> stringResource(R.string.groups_error_not_found)
    is AppError.Conflict -> message ?: stringResource(R.string.groups_error_conflict)
    is AppError.Validation -> message ?: stringResource(R.string.groups_error_validation)
    is AppError.Server -> message ?: stringResource(R.string.groups_error_server)
    is AppError.Unexpected -> message ?: stringResource(R.string.groups_error_unexpected)
}

/** Traduce el error que Paging propaga como excepción en sus `LoadState`. */
@Composable
fun Throwable.toGroupsMessage(): String =
    (this as? ApiException)?.error?.toMessage()
        ?: stringResource(R.string.groups_error_unexpected)

@Composable
fun ValidationIssue.toMessage(): String = when (this) {
    ValidationIssue.Required -> stringResource(R.string.groups_error_required)
    ValidationIssue.InvalidEmail -> stringResource(R.string.groups_error_invalid_email)
    is ValidationIssue.TooShort -> stringResource(R.string.groups_error_too_short, min)
    is ValidationIssue.TooLong -> stringResource(R.string.groups_error_too_long, max)
    ValidationIssue.InvalidCurrency -> stringResource(R.string.groups_error_invalid_currency)
}

@Composable
fun GroupRole.label(): String = when (this) {
    GroupRole.OWNER -> stringResource(R.string.group_role_owner)
    GroupRole.ADMIN -> stringResource(R.string.group_member_role_admin)
    GroupRole.MEMBER -> stringResource(R.string.group_member_role_member)
}

/** Etiqueta del estado del miembro, o `null` cuando no hay nada que mostrar. */
@Composable
fun MemberStatus.label(): String? = when (this) {
    MemberStatus.INVITED -> stringResource(R.string.group_status_invited)
    MemberStatus.ACTIVE, MemberStatus.LEFT -> null
}
