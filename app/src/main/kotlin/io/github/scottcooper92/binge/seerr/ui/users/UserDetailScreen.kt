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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.binge.designsystem.component.BingeSnackbarHost
import com.binge.designsystem.component.BingeTag
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.DetailStat
import com.binge.designsystem.component.DetailStatRow
import com.binge.designsystem.component.MediaCard
import com.binge.designsystem.component.MediaCarousel
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import com.binge.designsystem.formatRelativeOrAbsolute
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.hub.QuotaSection
import io.github.scottcooper92.binge.seerr.ui.openInBrowser
import io.github.scottcooper92.binge.seerr.ui.requests.PagedAppendState
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestRow
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import com.binge.designsystem.R as DesR

class UserDetailActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenRequest: (RequestItem) -> Unit,
    val onDeleteUser: () -> Unit,
)

/**
 * One user as a page: who they are, their quota and permissions, their requests, and, where the
 * server has them, what they watched and want to watch. A title opens on the server, as the
 * request page's does.
 */
@Composable
fun UserDetailScreen(
    state: UserDetailUiState,
    requests: Flow<PagingData<RequestItem>>,
    events: Flow<UserDetailEvent>,
    actions: UserDetailActions,
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
    Scaffold(
        snackbarHost = { BingeSnackbarHost(snackbarHostState) },
        topBar = {
            BingeTopBar(
                title = ready?.detail?.item?.name ?: stringResource(R.string.user_detail_title),
                onBack = actions.onBack,
                actions = {
                    if (ready != null) {
                        IconButton(onClick = { managing = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.user_actions_cd))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                UserDetailUiState.Loading -> LoadingScreen()
                is UserDetailUiState.Error -> ErrorScreen(error = state.error, onRetry = actions.onRetry)
                is UserDetailUiState.Ready -> UserDetailContent(state.detail, requests.collectAsLazyPagingItems(), actions)
            }
        }
    }
    if (managing && ready != null) {
        UserActionsSheet(detail = ready.detail, deleting = ready.deleting, onDeleteUser = actions.onDeleteUser, onDismiss = {
            managing =
                false
        })
    }
}

@Composable
private fun UserDetailContent(
    detail: UserDetail,
    requests: LazyPagingItems<RequestItem>,
    actions: UserDetailActions,
) {
    val context = LocalContext.current
    val inset = dimensionResource(DesR.dimen.screen_content_inset)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = dimensionResource(DesR.dimen.padding_l)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        item { ProfileHeader(detail.item, modifier = Modifier.padding(inset)) }
        item { DetailStatRow(userStats(detail)) }
        detail.quota?.let { quota -> item { Box(Modifier.padding(horizontal = inset)) { QuotaSection(quota) } } }
        if (detail.permissions.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(R.string.user_permissions_title))
                FlowRow(
                    modifier = Modifier.padding(horizontal = inset),
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
                ) {
                    detail.permissions.forEach { permission -> BingeTag(label = stringResource(permission.labelRes())) }
                }
            }
        }
        detail.watch?.takeIf { it.recentlyWatched.isNotEmpty() }?.let { watch ->
            item { TitleCarousel(stringResource(R.string.user_recently_watched), watch.recentlyWatched, detail.webUrl) }
        }
        if (detail.watchlist.isNotEmpty()) {
            item { TitleCarousel(stringResource(R.string.user_watchlist), detail.watchlist, detail.webUrl) }
        }
        item { SectionHeader(title = stringResource(R.string.hub_section_requests)) }
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
                    Text(
                        stringResource(R.string.user_no_requests),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = inset),
                    )
                refresh is LoadState.Loading || refresh is LoadState.Error ->
                    PagedAppendState(refresh, onRetry = requests::retry, onReconnect = actions.onBack)
                else -> PagedAppendState(requests.loadState.append, onRetry = requests::retry, onReconnect = actions.onBack)
            }
        }
        item {
            Text(
                stringResource(R.string.request_open_web),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clickable { context.openInBrowser(detail.webUrl) }.padding(inset),
            )
        }
    }
}

@Composable
private fun userStats(detail: UserDetail): List<DetailStat> =
    listOfNotNull(
        DetailStat(Icons.Filled.Inbox, detail.item.requestCount.toString(), stringResource(R.string.hub_section_requests)),
        formatRelativeOrAbsolute(detail.item.createdAtMillis)?.let { joined ->
            DetailStat(Icons.Filled.CalendarMonth, joined, stringResource(R.string.user_joined))
        },
        detail.watch?.playCount?.let { DetailStat(Icons.Filled.PlayArrow, it.toString(), stringResource(R.string.user_plays)) },
    )

/** The identity block: a large avatar over the name, how they sign in, the role and server tags, and the email. */
@Composable
private fun ProfileHeader(
    item: UserItem,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
    ) {
        BingeInitialsAvatar(name = item.name, avatarUrl = item.avatarUrl, size = dimensionResource(DesR.dimen.avatar_size_lg))
        Text(item.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        item.handle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
            BingeTag(label = stringResource(if (item.isAdmin) R.string.hub_role_admin else R.string.hub_role_user))
            BingeTag(label = stringResource(item.origin.labelRes()))
        }
        item.email?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** A carousel of titles; each card opens the title on the server, which has the page this app does not. */
@Composable
private fun TitleCarousel(
    title: String,
    items: List<TitleCardItem>,
    webUrl: String,
) {
    val context = LocalContext.current
    val serverRoot = webUrl.substringBefore("users/")
    MediaCarousel(title = title, items = items, itemKey = { it.mediaType.name + it.tmdbId }, onMoreClick = null) { item ->
        MediaCard(
            posterUrl = item.posterUrl,
            title =
                item.title
                    ?: stringResource(if (item.mediaType == RequestMediaType.Tv) R.string.media_type_tv else R.string.media_type_movie),
            rating = null,
            onClick = {
                context.openInBrowser(
                    serverRoot + (
                        if (item.mediaType ==
                            RequestMediaType.Tv
                        ) {
                            "tv/"
                        } else {
                            "movie/"
                        }
                    ) + item.tmdbId,
                )
            },
        )
    }
}

/** The page's overflow: the user on the server, and, where the guard allows, deleting them behind a confirm. */
@Composable
private fun UserActionsSheet(
    detail: UserDetail,
    deleting: Boolean,
    onDeleteUser: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    BingeBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = dimensionResource(DesR.dimen.padding_l))) {
            ActionRow(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.request_open_web)) {
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
