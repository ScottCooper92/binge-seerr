package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.component.BingeTag
import com.binge.designsystem.component.DetailStat
import com.binge.designsystem.component.DetailStatRow
import com.binge.designsystem.component.MediaCard
import com.binge.designsystem.component.MediaCarousel
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.resolvedContentInset
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.hub.QuotaSection
import io.github.scottcooper92.binge.seerr.ui.openInBrowser
import io.github.scottcooper92.binge.seerr.ui.openTitle
import io.github.scottcooper92.binge.seerr.ui.requests.PagedAppendState
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestRow
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.OverflowDetailScaffold
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import com.binge.designsystem.R as DesR

class UserDetailActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenRequest: (RequestItem) -> Unit,
    val onOpenSettings: () -> Unit,
    val onDeleteUser: () -> Unit,
)

/**
 * One user as a page: who they are, their quota and permissions, their requests, and, where the
 * server has them, what they watched and want to watch. A title opens on the server, as the
 * request page's does.
 *
 * @param showBack false when this user is the only thing in the detail pane beside the hub — opened
 * from the hub's own account card — where a back arrow to the hub is redundant.
 */
@Composable
fun UserDetailScreen(
    state: UserDetailUiState,
    requests: Flow<PagingData<RequestItem>>,
    events: Flow<UserDetailEvent>,
    actions: UserDetailActions,
    showBack: Boolean = true,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collectLatest { event ->
            when (event) {
                UserDetailEvent.UserDeleted -> actions.onBack()
                is UserDetailEvent.Failed -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = resources.getString(event.error.messageRes()),
                        kind = SnackbarMessageKind.Error,
                    )
                }
            }
        }
    }
    var managing by rememberSaveable { mutableStateOf(false) }
    val ready = state as? UserDetailUiState.Ready
    val seeded = state as? UserDetailUiState.Seeded
    OverflowDetailScaffold(
        title = ready?.detail?.item?.name ?: seeded?.item?.name ?: stringResource(R.string.user_detail_title),
        onBack = actions.onBack.takeIf { showBack },
        snackbarHostState = snackbarHostState,
        showOverflow = ready != null,
        overflowContentDescription = stringResource(R.string.user_actions_cd),
        onOverflowClick = { managing = true },
    ) { inner ->
        when (state) {
            UserDetailUiState.Loading -> UserDetailSkeleton(Modifier.padding(inner))
            is UserDetailUiState.Error ->
                ErrorScreen(error = state.error, modifier = Modifier.padding(inner), onRetry = actions.onRetry)
            is UserDetailUiState.Seeded ->
                UserDetailContent(state.item, null, state.error, requests.collectAsLazyPagingItems(), actions, inner)
            is UserDetailUiState.Ready ->
                UserDetailContent(state.detail.item, state.detail, null, requests.collectAsLazyPagingItems(), actions, inner)
        }
    }
    if (managing && ready != null) {
        UserActionsSheet(
            detail = ready.detail,
            deleting = ready.deleting,
            onOpenSettings = actions.onOpenSettings,
            onDeleteUser = actions.onDeleteUser,
            onDismiss = { managing = false },
        )
    }
}

@Composable
private fun UserDetailContent(
    item: UserItem,
    detail: UserDetail?,
    refreshError: SeerrError?,
    requests: LazyPagingItems<RequestItem>,
    actions: UserDetailActions,
    contentPadding: PaddingValues,
) {
    val inset = resolvedContentInset()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = dimensionResource(DesR.dimen.padding_l)) + contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        item {
            ProfileHeader(
                item,
                modifier = Modifier.padding(inset).layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.PROFILE)),
            )
        }
        // A failed refresh says so under the profile, which stays; the requests below are their own stream.
        refreshError?.let { error -> item { ErrorScreen(error = error, onRetry = actions.onRetry) } }
        if (detail?.watch?.playCount != null) {
            item {
                DetailStatRow(
                    stats = userStats(detail),
                    modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.STATS)),
                )
            }
        }
        detail?.quota?.let { quota -> item { Box(Modifier.padding(horizontal = inset)) { QuotaSection(quota) } } }
        // The bitmask is on the row, so a seeded page names the permissions as the loaded one will.
        val permissions = detail?.permissions ?: ManageablePermission.decode(item.permissions)
        if (permissions.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(R.string.user_permissions_title))
                FlowRow(
                    modifier = Modifier.padding(horizontal = inset),
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
                ) {
                    permissions.forEach { permission -> BingeTag(label = stringResource(permission.labelRes())) }
                }
            }
        }
        detail?.watch?.takeIf { it.recentlyWatched.isNotEmpty() }?.let { watch ->
            item { TitleCarousel(stringResource(R.string.user_recently_watched), watch.recentlyWatched, detail.serverUrl) }
        }
        if (detail != null && detail.watchlist.isNotEmpty()) {
            item { TitleCarousel(stringResource(R.string.user_watchlist), detail.watchlist, detail.serverUrl) }
        }
        item {
            SectionHeader(
                title = stringResource(R.string.hub_section_requests),
                trailingContent = {
                    Text(
                        item.requestCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
        items(count = requests.itemCount, key = requests.itemKey { it.id }) { index ->
            requests[index]?.let { item ->
                RequestRow(
                    item = item,
                    onClick = { actions.onOpenRequest(item) },
                    modifier = Modifier.padding(horizontal = inset).padding(bottom = dimensionResource(DesR.dimen.list_row_spacing)),
                )
            }
        }
        item {
            val refresh = requests.loadState.refresh
            when {
                refresh is LoadState.NotLoading && requests.itemCount == 0 ->
                    EmptyScreen(
                        message = stringResource(R.string.user_no_requests),
                        modifier = Modifier.padding(vertical = dimensionResource(DesR.dimen.padding_l)),
                        icon = Icons.Filled.Inbox,
                    )
                refresh is LoadState.Loading || refresh is LoadState.Error ->
                    PagedAppendState(refresh, onRetry = requests::retry, onReconnect = actions.onBack)
                else -> PagedAppendState(requests.loadState.append, onRetry = requests::retry, onReconnect = actions.onBack)
            }
        }
    }
}

@Composable
private fun userStats(detail: UserDetail): List<DetailStat> =
    listOfNotNull(
        detail.watch?.playCount?.let { DetailStat(Icons.Filled.PlayArrow, it.toString(), stringResource(R.string.user_plays)) },
    )

/** The identity block: the avatar with everything else beside it, the username, the display name and email, then the role and server tags. */
@Composable
private fun ProfileHeader(
    item: UserItem,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        BingeInitialsAvatar(name = item.name, avatarUrl = item.avatarUrl, size = dimensionResource(DesR.dimen.avatar_size_lg))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs))) {
            Text(item.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val joined = formatRelativeOrAbsolute(item.createdAtMillis)?.let { stringResource(R.string.user_joined, it) }
            val username = item.handle ?: item.email
            listOfNotNull(
                listOfNotNull(username, joined).joinToString(stringResource(R.string.hub_meta_separator)).ifEmpty {
                    null
                },
                item.email?.takeUnless { it.equals(username, ignoreCase = true) },
            ).forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
                BingeTag(label = stringResource(if (item.isAdmin) R.string.hub_role_admin else R.string.hub_role_user))
                BingeTag(label = stringResource(item.origin.labelRes()))
            }
        }
    }
}

/** A carousel of titles; each card opens the title in Binge, or on the server where Binge is not installed. */
@Composable
private fun TitleCarousel(
    title: String,
    items: List<TitleCardItem>,
    serverRoot: String,
) {
    val context = LocalContext.current
    MediaCarousel(title = title, items = items, itemKey = { it.mediaType.name + it.tmdbId }, onMoreClick = null) { _, item ->
        MediaCard(
            posterUrl = item.posterUrl,
            title =
                item.title
                    ?: stringResource(if (item.mediaType == RequestMediaType.Tv) R.string.media_type_tv else R.string.media_type_movie),
            rating = null,
            onClick = {
                val path = if (item.mediaType == RequestMediaType.Tv) "tv/" else "movie/"
                context.openTitle(item.mediaType, item.tmdbId, serverRoot + path + item.tmdbId)
            },
        )
    }
}

/** The page's overflow: their settings and the user on the server, and, where the guard allows, deleting them behind a confirm. */
@Composable
private fun UserActionsSheet(
    detail: UserDetail,
    deleting: Boolean,
    onOpenSettings: () -> Unit,
    onDeleteUser: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    BingeBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = dimensionResource(DesR.dimen.padding_l))) {
            if (detail.canEditSettings) {
                ActionRow(Icons.Filled.Settings, stringResource(R.string.user_settings_title)) {
                    onDismiss()
                    onOpenSettings()
                }
            }
            ActionRow(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.open_in_named, detail.serverName)) {
                onDismiss()
                context.openInBrowser(detail.webUrl)
            }
            if (detail.canDelete) {
                ActionRow(
                    Icons.Filled.Delete,
                    stringResource(R.string.user_delete),
                    tint = MaterialTheme.colorScheme.error,
                    enabled = !deleting,
                ) {
                    confirmingDelete = true
                }
            }
        }
    }
    if (confirmingDelete) {
        BingeConfirmDialog(
            title = stringResource(R.string.user_delete_confirm_title, detail.item.name),
            message = stringResource(R.string.user_delete_confirm_message),
            confirmLabel = stringResource(R.string.user_delete),
            destructive = true,
            onConfirm = {
                confirmingDelete = false
                onDismiss()
                onDeleteUser()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m), vertical = dimensionResource(DesR.dimen.padding_s)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
