package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeTag
import com.binge.designsystem.component.ExpandableOverview
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.IconButtonTone
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.resolvedContentPadding
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.theme.BingeTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.openTitle
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes
import io.github.scottcooper92.binge.seerr.ui.state.MediaHeroDetailPage
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import com.binge.designsystem.R as DesR

class BlocklistDetailActions(
    val onBack: () -> Unit,
    val onUnblock: () -> Unit,
)

/**
 * One blocked title as a page: the hero over its backdrop, who blocked it and when, the tags it
 * fell to, and Unblock as the primary action where this viewer may use it. This app renders no
 * title page: the title itself opens in Binge, or on the server, from the top bar's open icon —
 * the row's own former hand-off, moved here now that a tap opens this page instead.
 */
@Composable
fun BlocklistDetailScreen(
    state: BlocklistDetailUiState,
    events: Flow<BlocklistDetailEvent>,
    actions: BlocklistDetailActions,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var confirming by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(events) {
        events.collectLatest { event ->
            when (event) {
                BlocklistDetailEvent.Removed -> actions.onBack()
                is BlocklistDetailEvent.Failed -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(resources.getString(event.error.messageRes()), SnackbarMessageKind.Error)
                }
            }
        }
    }
    BlocklistDetailPage(state = state, onBack = actions.onBack, onPrimary = { confirming = true }, snackbarHostState = snackbarHostState)
    if (confirming) {
        BingeConfirmDialog(
            title =
                stringResource(
                    R.string.blocklist_unblock_confirm_title,
                    state.item.title ?: stringResource(R.string.blocklist_untitled),
                ),
            message = stringResource(R.string.blocklist_unblock_confirm_message),
            confirmLabel = stringResource(R.string.blocklist_unblock),
            onConfirm = {
                confirming = false
                actions.onUnblock()
            },
            onDismiss = { confirming = false },
        )
    }
}

/**
 * The page itself, on the shape [MediaHeroDetailPage] shares with
 * [io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailPage]: this composable is what feeds
 * it — the title/backdrop/year, the open action, and [BlocklistUnblockAction] as the footer, dropped
 * entirely for a viewer without `MANAGE_BLOCKLIST`.
 */
@Composable
internal fun BlocklistDetailPage(
    state: BlocklistDetailUiState,
    onBack: () -> Unit,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val item = state.item
    val title = item.title ?: stringResource(item.mediaType.labelRes())
    val context = LocalContext.current
    val footer: (@Composable () -> Unit)? =
        if (state.canManage) {
            { BlocklistUnblockAction(unblocking = state.unblocking, onClick = onPrimary) }
        } else {
            null
        }
    MediaHeroDetailPage(
        title = title,
        backdropUrl = state.backdropUrl,
        metaText = item.year.orEmpty(),
        onBack = onBack,
        modifier = modifier,
        scrollState = scrollState,
        snackbarHostState = snackbarHostState,
        topBarActions = {
            if (state.webUrl.isNotEmpty()) {
                ExpressiveIconButton(
                    onClick = { context.openTitle(item.mediaType, item.tmdbId, state.webUrl) },
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.request_open_elsewhere),
                    tint = BingeTheme.colors.onScrim,
                    tone = IconButtonTone.Glass,
                    size = dimensionResource(DesR.dimen.top_bar_icon_size),
                )
            }
        },
        footer = footer,
        body = {
            state.overview?.let { overview ->
                ExpandableOverview(
                    text = overview,
                    modifier = Modifier.padding(resolvedContentPadding(top = resolvedContentInset(), bottom = resolvedContentInset())),
                )
            }
            BlocklistDetailFacts(item)
        },
    )
}

/** Who blocked it, when, and the tags it fell to — the row's own facts, read as a page instead of a line. */
@Composable
private fun BlocklistDetailFacts(item: BlocklistItem) {
    val rows =
        listOfNotNull(
            item.addedBy?.let {
                ListItem(
                    icon = Icons.Filled.Person,
                    label = stringResource(R.string.blocklist_detail_blocked_by),
                    detail = it,
                    clickable = false,
                )
            },
            formatRelativeOrAbsolute(item.addedAtMillis)?.let {
                ListItem(
                    icon = Icons.Filled.Schedule,
                    label = stringResource(R.string.blocklist_detail_blocked_at),
                    detail = it,
                    clickable = false,
                )
            },
        )
    if (rows.isNotEmpty()) {
        ItemGroup(title = null, rows = rows, modifier = Modifier.padding(resolvedContentPadding()))
    }
    if (item.tags.isNotEmpty()) {
        SectionHeader(title = stringResource(R.string.request_tags))
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(resolvedContentPadding()),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
        ) {
            item.tags.forEach { tag -> BingeTag(label = tag) }
        }
    }
}

/**
 * Rounded and raised, the same [BingeShapes.HeroTop] container
 * [io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailScreen]'s own primary action uses,
 * so the shape reads the same wherever a page pins its one action below the scroll.
 */
@Composable
private fun BlocklistUnblockAction(
    unblocking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BingeActionFooter(
        label = stringResource(R.string.blocklist_unblock),
        onClick = onClick,
        modifier = modifier,
        loading = unblocking,
        shape = BingeShapes.HeroTop,
        shadowElevation = dimensionResource(DesR.dimen.snackbar_elevation),
        horizontalPadding = resolvedContentInset(),
        clearsNavigationBar = true,
    )
}
