package app.cuentasclaras.feature.groups.create

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import app.cuentasclaras.core.designsystem.component.AppTextField
import app.cuentasclaras.core.designsystem.component.ErrorBanner
import app.cuentasclaras.core.designsystem.component.PrimaryButton
import app.cuentasclaras.domain.validation.GroupField
import app.cuentasclaras.feature.groups.R
import app.cuentasclaras.feature.groups.toMessage

@Composable
fun CreateGroupEntry(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateGroupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdGroupId) {
        if (state.createdGroupId != null) {
            viewModel.onEvent(CreateGroupEvent.CreatedGroupHandled)
            onBack()
        }
    }

    CreateGroupScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    state: CreateGroupUiState,
    onEvent: (CreateGroupEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_group_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.create_group_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            AppTextField(
                value = state.name,
                onValueChange = { onEvent(CreateGroupEvent.NameChanged(it)) },
                label = stringResource(R.string.create_group_name),
                enabled = !state.isSubmitting,
                isError = state.fieldIssues.containsKey(GroupField.NAME),
                supportingText = state.fieldIssues[GroupField.NAME]?.toMessage(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )

            Spacer(Modifier.height(16.dp))

            AppTextField(
                value = state.description,
                onValueChange = { onEvent(CreateGroupEvent.DescriptionChanged(it)) },
                label = stringResource(R.string.create_group_description),
                enabled = !state.isSubmitting,
                isError = state.fieldIssues.containsKey(GroupField.DESCRIPTION),
                supportingText = state.fieldIssues[GroupField.DESCRIPTION]?.toMessage(),
                imeAction = ImeAction.Next,
            )

            Spacer(Modifier.height(16.dp))

            AppTextField(
                value = state.currency,
                onValueChange = { onEvent(CreateGroupEvent.CurrencyChanged(it)) },
                label = stringResource(R.string.create_group_currency),
                enabled = !state.isSubmitting,
                isError = state.fieldIssues.containsKey(GroupField.CURRENCY),
                supportingText = state.fieldIssues[GroupField.CURRENCY]?.toMessage(),
                imeAction = ImeAction.Done,
            )

            state.error?.let { error ->
                Spacer(Modifier.height(16.dp))
                ErrorBanner(message = error.toMessage())
            }

            Spacer(Modifier.height(24.dp))

            PrimaryButton(
                text = stringResource(R.string.create_group_submit),
                onClick = { onEvent(CreateGroupEvent.Submitted) },
                enabled = state.canSubmit,
                loading = state.isSubmitting,
            )
        }
    }
}
