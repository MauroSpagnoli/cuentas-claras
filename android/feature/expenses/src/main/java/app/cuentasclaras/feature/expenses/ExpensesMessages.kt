package app.cuentasclaras.feature.expenses

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.cuentasclaras.domain.error.ApiException
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.SplitType
import app.cuentasclaras.domain.validation.ExpenseIssue

/**
 * Traducciones de los tipos de dominio a textos localizados. Todo el mapeo vive
 * aquí para que las pantallas no repitan los `when`.
 */

@Composable
fun AppError.toMessage(): String = when (this) {
    AppError.Network -> stringResource(R.string.expenses_error_network)
    AppError.Unauthorized -> stringResource(R.string.expenses_error_unauthorized)
    AppError.Forbidden -> stringResource(R.string.expenses_error_forbidden)
    AppError.NotFound -> stringResource(R.string.expenses_error_not_found)
    is AppError.Conflict -> message ?: stringResource(R.string.expenses_error_conflict)
    is AppError.Validation -> message ?: stringResource(R.string.expenses_error_validation)
    is AppError.Server -> message ?: stringResource(R.string.expenses_error_server)
    is AppError.Unexpected -> message ?: stringResource(R.string.expenses_error_unexpected)
}

@Composable
fun Throwable.toMessage(): String =
    (this as? ApiException)?.error?.toMessage() ?: stringResource(R.string.expenses_error_unexpected)

/**
 * Problema de validación del formulario. [currency] solo hace falta para los
 * mensajes que muestran importes.
 */
@Composable
fun ExpenseIssue.toMessage(currency: String): String = when (this) {
    ExpenseIssue.Required -> stringResource(R.string.expense_issue_required)
    is ExpenseIssue.TooLong -> stringResource(R.string.expense_issue_too_long, max)
    ExpenseIssue.InvalidAmount -> stringResource(R.string.expense_issue_invalid_amount)
    ExpenseIssue.AmountNotPositive -> stringResource(R.string.expense_issue_amount_not_positive)
    is ExpenseIssue.AmountTooLarge -> stringResource(R.string.expense_issue_amount_too_large)
    ExpenseIssue.InvalidNumber -> stringResource(R.string.expense_issue_invalid_number)
    ExpenseIssue.NegativeNumber -> stringResource(R.string.expense_issue_negative_number)
    ExpenseIssue.NoParticipants -> stringResource(R.string.expense_issue_no_participants)
    ExpenseIssue.PayerNotMember -> stringResource(R.string.expense_issue_payer_not_member)
    is ExpenseIssue.ExactSumMismatch -> stringResource(
        R.string.expense_issue_exact_mismatch,
        ExpensesFormat.amount(actualMinor, currency),
        ExpensesFormat.amount(expectedMinor, currency),
    )
    is ExpenseIssue.PercentageSumNotHundred -> stringResource(
        R.string.expense_issue_percentage_not_hundred,
        ExpensesFormat.decimal(actual),
    )
    ExpenseIssue.SharesAllZero -> stringResource(R.string.expense_issue_shares_all_zero)
}

@Composable
fun SplitType.label(): String = when (this) {
    SplitType.EQUAL -> stringResource(R.string.split_type_equal)
    SplitType.EXACT -> stringResource(R.string.split_type_exact)
    SplitType.PERCENTAGE -> stringResource(R.string.split_type_percentage)
    SplitType.SHARES -> stringResource(R.string.split_type_shares)
}
