package app.cuentasclaras.feature.groups.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import app.cuentasclaras.core.designsystem.component.AppTextField
import app.cuentasclaras.core.designsystem.component.ErrorBanner
import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.feature.groups.R
import app.cuentasclaras.feature.groups.toGroupsMessage
import app.cuentasclaras.feature.groups.toMessage
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun GroupsListEntry(
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val groups = viewModel.groups.collectAsLazyPagingItems()

    LaunchedEffect(state.joinedGroupId) {
        val joined = state.joinedGroupId ?: return@LaunchedEffect
        onOpenGroup(joined)
        viewModel.onEvent(GroupsListEvent.JoinedGroupHandled)
    }

    GroupsListScreen(
        state = state,
        groups = groups,
        onEvent = viewModel::onEvent,
        onOpenGroup = onOpenGroup,
        onCreateGroup = onCreateGroup,
        onSignOut = onSignOut,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsListScreen(
    state: GroupsListUiState,
    groups: LazyPagingItems<Group>,
    onEvent: (GroupsListEvent) -> Unit,
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.groups_title)) },
                actions = {
                    IconButton(onClick = { groups.refresh() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.groups_refresh),
                        )
                    }
                    TextButton(onClick = onSignOut) {
                        Text(stringResource(R.string.groups_sign_out))
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                ExtendedFloatingActionButton(
                    onClick = { onEvent(GroupsListEvent.JoinDialogOpened) },
                    content = { Text(stringResource(R.string.groups_join)) },
                )
                Spacer(Modifier.height(8.dp))
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.groups_create)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = onCreateGroup,
                )
            }
        },
    ) { padding ->
        GroupsListContent(
            groups = groups,
            onOpenGroup = onOpenGroup,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }

    if (state.joinDialogVisible) {
        JoinGroupDialog(
            state = state,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun GroupsListContent(
    groups: LazyPagingItems<Group>,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh = groups.loadState.refresh

    when {
        refresh is LoadState.Loading && groups.itemCount == 0 -> CenteredMessage(
            message = stringResource(R.string.groups_loading),
            loading = true,
            modifier = modifier,
        )

        refresh is LoadState.Error && groups.itemCount == 0 -> Column(
            modifier = modifier.padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ErrorBanner(message = refresh.error.toGroupsMessage())
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = groups::retry) {
                Text(stringResource(R.string.groups_retry))
            }
        }

        groups.itemCount == 0 -> EmptyGroups(modifier = modifier)

        else -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            items(
                count = groups.itemCount,
                key = groups.itemKey { it.id },
            ) { index ->
                groups[index]?.let { group ->
                    GroupRow(
                        group = group,
                        onClick = { onOpenGroup(group.id) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            when (val append = groups.loadState.append) {
                is LoadState.Loading -> item {
                    CenteredMessage(
                        message = stringResource(R.string.groups_loading_more),
                        loading = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is LoadState.Error -> item {
                    Column(Modifier.padding(16.dp)) {
                        ErrorBanner(message = append.error.toGroupsMessage())
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = groups::retry) {
                            Text(stringResource(R.string.groups_retry))
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: Group,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = group.description ?: stringResource(R.string.group_no_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(
                        R.plurals.groups_member_count,
                        group.memberCount,
                        group.memberCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.group_currency_value, group.currency),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun EmptyGroups(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.groups_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.groups_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CenteredMessage(
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun JoinGroupDialog(
    state: GroupsListUiState,
    onEvent: (GroupsListEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(GroupsListEvent.JoinDialogDismissed) },
        title = { Text(stringResource(R.string.join_group_title)) },
        text = {
            Column {
                AppTextField(
                    value = state.joinCode,
                    onValueChange = { onEvent(GroupsListEvent.JoinCodeChanged(it)) },
                    label = stringResource(R.string.join_group_code),
                    enabled = !state.isJoining,
                )
                if (state.joinError != null) {
                    Spacer(Modifier.height(12.dp))
                    ErrorBanner(message = state.joinError.toMessage())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(GroupsListEvent.JoinSubmitted) },
                enabled = state.canSubmitJoin,
            ) {
                Text(stringResource(R.string.join_group_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(GroupsListEvent.JoinDialogDismissed) }) {
                Text(stringResource(R.string.join_group_cancel))
            }
        },
    )
}
