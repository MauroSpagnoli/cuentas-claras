package app.cuentasclaras.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cuentasclaras.core.designsystem.component.AppTextField
import app.cuentasclaras.core.designsystem.component.ErrorBanner
import app.cuentasclaras.core.designsystem.component.PrimaryButton
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.validation.AuthField
import app.cuentasclaras.domain.validation.ValidationIssue

@Composable
fun AuthRoute(
    onSwitchMode: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onSwitchMode = onSwitchMode,
        modifier = modifier,
    )
}

@Composable
fun AuthScreen(
    state: AuthUiState,
    onEvent: (AuthEvent) -> Unit,
    onSwitchMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    if (state.isRegister) R.string.auth_title_register else R.string.auth_title_login,
                ),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(
                    if (state.isRegister) R.string.auth_subtitle_register else R.string.auth_subtitle_login,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            state.error?.let { error ->
                ErrorBanner(message = error.toMessage())
                Spacer(Modifier.height(16.dp))
            }

            if (state.isRegister) {
                AppTextField(
                    value = state.displayName,
                    onValueChange = { onEvent(AuthEvent.DisplayNameChanged(it)) },
                    label = stringResource(R.string.auth_display_name),
                    enabled = !state.isSubmitting,
                    isError = state.fieldIssues.containsKey(AuthField.DISPLAY_NAME),
                    supportingText = state.fieldIssues[AuthField.DISPLAY_NAME]?.toMessage(),
                )
                Spacer(Modifier.height(12.dp))
            }

            AppTextField(
                value = state.email,
                onValueChange = { onEvent(AuthEvent.EmailChanged(it)) },
                label = stringResource(R.string.auth_email),
                enabled = !state.isSubmitting,
                isError = state.fieldIssues.containsKey(AuthField.EMAIL),
                supportingText = state.fieldIssues[AuthField.EMAIL]?.toMessage(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            Spacer(Modifier.height(12.dp))

            AppTextField(
                value = state.password,
                onValueChange = { onEvent(AuthEvent.PasswordChanged(it)) },
                label = stringResource(R.string.auth_password),
                enabled = !state.isSubmitting,
                isError = state.fieldIssues.containsKey(AuthField.PASSWORD),
                supportingText = state.fieldIssues[AuthField.PASSWORD]?.toMessage(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                imeAction = if (state.isRegister) ImeAction.Next else ImeAction.Done,
                onImeAction = {
                    if (state.canSubmit) {
                        focusManager.clearFocus()
                        onEvent(AuthEvent.Submit)
                    }
                },
                visualTransformation = if (state.passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(onClick = { onEvent(AuthEvent.TogglePasswordVisibility) }) {
                        Text(
                            text = stringResource(
                                if (state.passwordVisible) R.string.auth_toggle_hide else R.string.auth_toggle_show,
                            ),
                        )
                    }
                },
            )

            if (state.isRegister) {
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    value = state.defaultCurrency,
                    onValueChange = { onEvent(AuthEvent.CurrencyChanged(it)) },
                    label = stringResource(R.string.auth_currency),
                    enabled = !state.isSubmitting,
                    isError = state.fieldIssues.containsKey(AuthField.DEFAULT_CURRENCY),
                    supportingText = state.fieldIssues[AuthField.DEFAULT_CURRENCY]?.toMessage(),
                    imeAction = ImeAction.Done,
                )
            }

            Spacer(Modifier.height(24.dp))

            PrimaryButton(
                text = stringResource(
                    if (state.isRegister) R.string.auth_submit_register else R.string.auth_submit_login,
                ),
                onClick = {
                    focusManager.clearFocus()
                    onEvent(AuthEvent.Submit)
                },
                enabled = state.canSubmit,
                loading = state.isSubmitting,
            )

            TextButton(
                onClick = onSwitchMode,
                enabled = !state.isSubmitting,
            ) {
                Text(
                    text = stringResource(
                        if (state.isRegister) R.string.auth_switch_to_login else R.string.auth_switch_to_register,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AppError.toMessage(): String = when (this) {
    AppError.Network -> stringResource(R.string.auth_error_network)
    AppError.Unauthorized -> stringResource(R.string.auth_error_unauthorized)
    AppError.Forbidden -> stringResource(R.string.auth_error_forbidden)
    AppError.NotFound -> stringResource(R.string.auth_error_not_found)
    is AppError.Conflict -> message ?: stringResource(R.string.auth_error_conflict)
    is AppError.Validation -> message ?: stringResource(R.string.auth_error_validation)
    is AppError.Server -> message ?: stringResource(R.string.auth_error_server)
    is AppError.Unexpected -> message ?: stringResource(R.string.auth_error_unexpected)
}

@Composable
private fun ValidationIssue.toMessage(): String = when (this) {
    ValidationIssue.Required -> stringResource(R.string.auth_error_required)
    ValidationIssue.InvalidEmail -> stringResource(R.string.auth_error_invalid_email)
    is ValidationIssue.TooShort -> stringResource(R.string.auth_error_password_too_short, min)
    is ValidationIssue.TooLong -> stringResource(R.string.auth_error_display_name_too_long, max)
    ValidationIssue.InvalidCurrency -> stringResource(R.string.auth_error_invalid_currency)
}
