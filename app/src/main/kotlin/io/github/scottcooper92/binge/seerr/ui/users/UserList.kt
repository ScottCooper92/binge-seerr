package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.component.BingeTag
import com.binge.designsystem.component.ListRow
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.PagedAppendState
import io.github.scottcooper92.binge.seerr.ui.requests.PagedRefreshError
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import com.binge.designsystem.R as DesR

/** The rows with the states the pager reports, as the issues browser shows them. */
@Composable
internal fun UsersBody(
    lazyItems: LazyPagingItems<UserItem>,
    selection: Set<Int>,
    onOpen: (UserItem) -> Unit,
    onToggleSelected: (UserItem) -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remote = lazyItems.loadState.mediator?.refresh ?: lazyItems.loadState.refresh
    when {
        lazyItems.itemCount > 0 ->
            Column(modifier.fillMaxSize()) {
                if (remote is LoadState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                UserList(lazyItems, selection, onOpen, onToggleSelected, onReconnect)
            }
        remote is LoadState.Loading || lazyItems.loadState.refresh is LoadState.Loading -> LoadingScreen(modifier)
        remote is LoadState.Error ->
            PagedRefreshError(
                remote.error,
                onRetry = lazyItems::retry,
                onReconnect = onReconnect,
                modifier = modifier,
            )
        else -> EmptyScreen(message = stringResource(R.string.users_empty), modifier = modifier, icon = Icons.Filled.People)
    }
}

@Composable
private fun UserList(
    lazyItems: LazyPagingItems<UserItem>,
    selection: Set<Int>,
    onOpen: (UserItem) -> Unit,
    onToggleSelected: (UserItem) -> Unit,
    onReconnect: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(dimensionResource(DesR.dimen.screen_content_inset)),
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
            PagedAppendState(append, onRetry = lazyItems::retry, onReconnect = onReconnect)
        }
    }
}

/** One user: avatar, name and role, how they sign in, and their request count; a long press starts selecting. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UserRow(
    item: UserItem,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListRow(
        modifier =
            modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.users_select_cd),
            ),
        verticalAlignment = Alignment.CenterVertically,
        leading = {
            BingeInitialsAvatar(
                name = item.name,
                avatarUrl = item.avatarUrl,
                size = dimensionResource(DesR.dimen.avatar_size_md),
            )
        },
        trailing = {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = null)
            } else {
                Text(
                    pluralStringResource(R.plurals.users_request_count, item.requestCount, item.requestCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) { contentModifier ->
        Column(modifier = contentModifier, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            ) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.isAdmin) BingeTag(label = stringResource(R.string.hub_role_admin))
            }
            Text(
                text =
                    listOfNotNull(item.handle ?: item.email, stringResource(item.origin.labelRes()))
                        .joinToString(stringResource(R.string.hub_meta_separator)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
