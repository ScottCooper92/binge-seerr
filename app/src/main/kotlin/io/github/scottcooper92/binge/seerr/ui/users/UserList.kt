package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListRowSkeletonColumn
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.resolvedContentPadding
import com.binge.designsystem.template.PagedPhase
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.data.ListRefresh
import io.github.scottcooper92.binge.seerr.ui.requests.PagedAppendState
import io.github.scottcooper92.binge.seerr.ui.requests.PagedRefreshError
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.PagedPullToRefresh
import io.github.scottcooper92.binge.seerr.ui.state.PullableMessage
import io.github.scottcooper92.binge.seerr.ui.state.RoleTag
import io.github.scottcooper92.binge.seerr.ui.state.rememberPagedPhase
import io.github.scottcooper92.binge.seerr.ui.users.settings.UserRole
import com.binge.designsystem.R as DesR

/**
 * The rows with the states the pager reports, as the issues browser shows them, under a pull that refreshes them.
 *
 * @param pullState a still frame's resting pull; null remembers M3's own. See [PagedPullToRefresh].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UsersBody(
    lazyItems: LazyPagingItems<UserItem>,
    /** The list's latest finished network refresh; see [rememberPagedPhase]. */
    lastRefresh: ListRefresh?,
    selection: Set<Int>,
    onOpen: (UserItem) -> Unit,
    onToggleSelected: (UserItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    pullState: PullToRefreshState? = null,
) {
    val phase = lazyItems.rememberPagedPhase(lastRefresh)
    PagedPullToRefresh(
        phase = phase,
        loadState = lazyItems.loadState,
        onRefresh = lazyItems::refresh,
        contentPadding = contentPadding,
        modifier = modifier,
        state = pullState,
    ) {
        when (phase) {
            is PagedPhase.Rows -> UserList(lazyItems, selection, onOpen, onToggleSelected, contentPadding)
            PagedPhase.Skeleton ->
                ListRowSkeletonColumn(
                    contentPadding = resolvedContentPadding(vertical = resolvedContentInset()) + contentPadding,
                    height = dimensionResource(R.dimen.users_row_skeleton_height),
                    modifier = Modifier.fillMaxSize(),
                )
            is PagedPhase.Failed ->
                PagedRefreshError(
                    phase.error,
                    onRetry = lazyItems::retry,
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
            PagedPhase.Empty ->
                PullableMessage { fill ->
                    EmptyScreen(
                        message = stringResource(R.string.users_empty),
                        modifier = fill.padding(contentPadding),
                        icon = Icons.Filled.People,
                    )
                }
        }
    }
}

@Composable
private fun UserList(
    lazyItems: LazyPagingItems<UserItem>,
    selection: Set<Int>,
    onOpen: (UserItem) -> Unit,
    onToggleSelected: (UserItem) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = resolvedContentPadding(vertical = resolvedContentInset()) + contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.list_row_spacing)),
    ) {
        items(count = lazyItems.itemCount, key = lazyItems.itemKey { it.id }) { index ->
            lazyItems[index]?.let { item ->
                UserRow(
                    item = item,
                    selecting = selection.isNotEmpty(),
                    selected = item.id in selection,
                    onClick = { if (selection.isEmpty()) onOpen(item) else onToggleSelected(item) },
                    onLongClick = { onToggleSelected(item) },
                )
            }
        }
        item {
            val append = lazyItems.loadState.mediator?.append ?: lazyItems.loadState.append
            PagedAppendState(append, onRetry = lazyItems::retry)
        }
    }
}

/** One user as a list item on its own card: name and how they sign in, their role and request count; a long press starts selecting. */
@Composable
internal fun UserRow(
    item: UserItem,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ItemGroup(
        title = null,
        modifier = modifier,
        rows =
            listOf(
                ListItem(
                    icon = Icons.Filled.Person,
                    leadingContent = {
                        BingeInitialsAvatar(
                            name = item.name,
                            avatarUrl = item.avatarUrl,
                            size = dimensionResource(DesR.dimen.avatar_size_md),
                        )
                    },
                    label = item.name,
                    detail =
                        listOfNotNull(item.handle ?: item.email, stringResource(item.origin.labelRes()))
                            .joinToString(stringResource(R.string.hub_meta_separator)),
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
                        ) {
                            if (item.role != UserRole.User) RoleTag(item.role)
                            if (selecting) {
                                Checkbox(checked = selected, onCheckedChange = null)
                            } else {
                                Text(
                                    pluralStringResource(R.plurals.users_request_count, item.requestCount, item.requestCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            ),
    )
}
