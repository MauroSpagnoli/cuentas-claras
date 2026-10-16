package app.cuentasclaras.feature.balances

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.cuentasclaras.domain.error.ApiException
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.validation.SettlementIssue
import java.util.Locale

/**
 * Traducciones de los tipos de dominio a textos localizados. Todo el mapeo vive
 * aquí para que las pantallas no repitan el `when`.
 */

@Composable
fun AppError.toMessage(): String = when (this) {
    AppError.Network -> stringResource(R.string.balances_error_network)
    AppError.Unauthorized -> stringResource(R.string.balances_error_unauthorized)
    AppError.Forbidden -> stringResource(R.string.balances_error_forbidden)
    AppError.NotFound -> stringResource(R.string.balances_error_not_found)
    is AppError.Conflict -> message ?: stringResource(R.string.balances_error_conflict)
    is AppError.Validation -> message ?: stringResource(R.string.balances_error_validation)
    is AppError.Server -> message ?: stringResource(R.string.balances_error_server)
    is AppError.Unexpected -> message ?: stringResource(R.string.balances_error_unexpected)
}

@Composable
fun Throwable.toMessage(): String =
    (this as? ApiException)?.error?.toMessage() ?: stringResource(R.string.balances_error_unexpected)

/** Problema de validación del formulario de pago. */
@Composable
fun SettlementIssue.toMessage(): String = when (this) {
    SettlementIssue.Required -> stringResource(R.string.settlement_issue_required)
    SettlementIssue.SameParty -> stringResource(R.string.settlement_issue_same_party)
    SettlementIssue.InvalidAmount -> stringResource(R.string.settlement_issue_invalid_amount)
    SettlementIssue.AmountNotPositive -> stringResource(R.string.settlement_issue_amount_not_positive)
    SettlementIssue.InvalidDate -> stringResource(R.string.settlement_issue_invalid_date)
    is SettlementIssue.TooLong -> stringResource(R.string.settlement_issue_too_long, max)
}

/** Nombre legible del algoritmo del plan (`MIN_EXACT`, `GREEDY`). */
@Composable
fun planAlgorithmLabel(algorithm: String): String = when (algorithm.uppercase(Locale.US)) {
    "MIN_EXACT" -> stringResource(R.string.balances_algorithm_min_exact)
    "GREEDY" -> stringResource(R.string.balances_algorithm_greedy)
    else -> algorithm
}
