package app.cuentasclaras.feature.groups.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cuentasclaras.core.designsystem.component.AppTextField
import app.cuentasclaras.core.designsystem.component.ErrorBanner
import app.cuentasclaras.core.designsystem.component.PrimaryButton
import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.model.GroupMember
import app.cuentasclaras.domain.model.GroupRole
import app.cuentasclaras.feature.groups.R
import app.cuentasclaras.feature.groups.label
import app.cuentasclaras.feature.groups.toMessage

@Composable
fun GroupDetailEntry(
    currentUserId: String,
    onBack: () -> Unit,
    onOpenExpenses: (groupId: String) -> Unit,
    onOpenBalances: (groupId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    GroupDetailScreen(
        state = state,
        currentUserId = currentUserId,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenExpenses = { onOpenExpenses(viewModel.groupId) },
        onOpenBalances = { onOpenBalances(viewModel.groupId) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    state: GroupDetailUiState,
    currentUserId: String,
    onEvent: (GroupDetailEvent) -> Unit,
    onBack: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenBalances: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.group?.name ?: stringResource(R.string.group_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.group_detail_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { onEvent(GroupDetailEvent.InviteRequested) }) {
                        Text(stringResource(R.string.group_detail_invite))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            state.error?.let { error ->
                ErrorBanner(
                    message = error.toMessage(),
                    modifier = Modifier.padding(16.dp),
                )
            }

            state.memberActionError?.let { error ->
                ErrorBanner(
                    message = error.toMessage(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            when {
                state.isLoading && state.group == null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    state.group?.let { group ->
                        item { GroupSummary(group = group) }
                    }

                    item {
                        NavigationRow(
                            label = stringResource(R.string.group_detail_expenses),
                            onClick = onOpenExpenses,
                        )
                    }

                    item {
                        NavigationRow(
                            label = stringResource(R.string.group_detail_balances),
                            onClick = onOpenBalances,
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.group_detail_members),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            val canManage = state.canManage(currentUserId)
                            OutlinedButton(
                                onClick = { onEvent(GroupDetailEvent.AddMemberOpened) },
                                enabled = canManage,
                            ) {
                                Text(stringResource(R.string.group_detail_add_member))
                            }
                        }
                    }

                    if (state.members.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.group_detail_no_members),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }

                    val canManage = state.canManage(currentUserId)
                    items(items = state.members, key = { it.userId }) { member ->
                        MemberRow(
                            member = member,
                            showActions = canManage &&
                                member.role != GroupRole.OWNER &&
                                member.userId != currentUserId,
                            busy = state.busyUserId == member.userId,
                            onEvent = onEvent,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (state.addMemberVisible) {
        AddMemberDialog(state = state, onEvent = onEvent)
    }

    if (state.inviteVisible) {
        InviteDialog(state = state, onEvent = onEvent)
    }
}

@Composable
private fun GroupSummary(group: Group, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = group.description ?: stringResource(R.string.group_no_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun NavigationRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
        )
    }
    HorizontalDivider()
}

@Composable
private fun MemberRow(
    member: GroupMember,
    showActions: Boolean,
    busy: Boolean,
    onEvent: (GroupDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = member.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        MemberTag(text = member.role.label())

        member.status.label()?.let { status ->
            Spacer(Modifier.width(8.dp))
            MemberTag(text = status)
        }

        if (busy) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else if (showActions) {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(
                        R.string.group_detail_member_actions,
                        member.displayName,
                    ),
                )
            }
            MemberActionsMenu(
                member = member,
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                onEvent = { event ->
                    menuOpen = false
                    onEvent(event)
                },
            )
        }
    }
}

@Composable
private fun MemberTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun MemberActionsMenu(
    member: GroupMember,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEvent: (GroupDetailEvent) -> Unit,
) {
    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val nextRole = if (member.role == GroupRole.ADMIN) GroupRole.MEMBER else GroupRole.ADMIN
        val label = if (nextRole == GroupRole.ADMIN) {
            stringResource(R.string.group_detail_make_admin)
        } else {
            stringResource(R.string.group_detail_make_member)
        }
        DropdownMenuItem(
            text = { Text(label) },
            onClick = { onEvent(GroupDetailEvent.MemberRoleChangeRequested(member.userId, nextRole)) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.group_detail_remove_member)) },
            onClick = { onEvent(GroupDetailEvent.MemberRemovalRequested(member.userId)) },
        )
    }
}

@Composable
private fun AddMemberDialog(
    state: GroupDetailUiState,
    onEvent: (GroupDetailEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(GroupDetailEvent.AddMemberDismissed) },
        title = { Text(stringResource(R.string.group_detail_join_title)) },
        text = {
            Column {
                AppTextField(
                    value = state.addMemberEmail,
                    onValueChange = { onEvent(GroupDetailEvent.AddMemberEmailChanged(it)) },
                    label = stringResource(R.string.group_detail_member_email),
                    enabled = !state.isAddingMember,
                    isError = state.addMemberIssue != null,
                    supportingText = state.addMemberIssue?.toMessage(),
                )
                state.addMemberError?.let { error ->
                    Spacer(Modifier.height(16.dp))
                    ErrorBanner(message = error.toMessage())
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(R.string.group_detail_member_submit),
                onClick = { onEvent(GroupDetailEvent.AddMemberSubmitted) },
                enabled = !state.isAddingMember && state.addMemberEmail.isNotBlank(),
                loading = state.isAddingMember,
            )
        },
        dismissButton = {
            TextButton(onClick = { onEvent(GroupDetailEvent.AddMemberDismissed) }) {
                Text(stringResource(R.string.group_detail_member_cancel))
            }
        },
    )
}

@Composable
private fun InviteDialog(
    state: GroupDetailUiState,
    onEvent: (GroupDetailEvent) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onEvent(GroupDetailEvent.InviteDismissed) },
        title = { Text(stringResource(R.string.group_detail_invite_title)) },
        text = {
            when {
                state.isCreatingInvite -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }

                state.inviteError != null -> ErrorBanner(message = state.inviteError.toMessage())

                state.invite != null -> Column {
                    Text(
                        text = stringResource(R.string.group_detail_invite_code),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = state.invite.code,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.group_detail_invite_url),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = state.invite.url,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (copied) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.group_detail_invite_copied),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                else -> Text(text = stringResource(R.string.groups_error_unexpected))
            }
        },
        confirmButton = {
            state.invite?.let { invite ->
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(invite.url))
                        copied = true
                    },
                ) {
                    Text(stringResource(R.string.group_detail_invite_copy))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(GroupDetailEvent.InviteDismissed) }) {
                Text(stringResource(R.string.group_detail_invite_close))
            }
        },
    )
}
