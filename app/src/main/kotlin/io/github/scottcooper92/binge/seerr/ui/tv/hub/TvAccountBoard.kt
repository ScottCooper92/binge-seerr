package io.github.scottcooper92.binge.seerr.ui.tv.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.tv.component.TvCardRow
import com.binge.designsystem.tv.focus.TvStableFocusScroll
import com.binge.designsystem.tv.focus.restoreTvOverlayFocus
import com.binge.designsystem.tv.focus.tvFocusGroup
import com.binge.designsystem.tv.nav.LocalTvContentInset
import com.binge.designsystem.tv.template.TvMessagePage
import com.binge.designsystem.tv.template.TvPageAction
import com.binge.designsystem.tv.template.TvScreenHeading
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes
import io.github.scottcooper92.binge.seerr.ui.requests.statusChip
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows
import io.github.scottcooper92.binge.seerr.ui.tv.TvPosterCard
import io.github.scottcooper92.binge.seerr.ui.tv.TvPosterIconChip
import io.github.scottcooper92.binge.seerr.ui.tv.tvColor
import io.github.scottcooper92.binge.seerr.ui.users.UserDetailUiState
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

private const val ROW_ITEM_CAP = 20

/** The requests row's slot in the page's list: the profile and the quota tiles come first. */
private const val REQUESTS_ITEM_INDEX = 2

private enum class AccountFocusArea { Profile, Requests }

/**
 * The account page: who is signed in as the phone's user page shows them — name, username and when they
 * joined, email, role and server tags, how many requests they have made — beside a tile for each request quota,
 * and below them the user's own requests as a row of posters. No backdrop: a row of the user's own requests
 * needs no description beyond its caption.
 *
 * [detail] is null until the account is known, and [accountFailed] says the account could not be resolved at all (the
 * hub could not load it), so the page offers a retry rather than loading forever; [requests] is the user's own paged list. [overlayOpen] is
 * whether a request's page is showing above the rail, so focus returns to the card that opened it.
 */
@Composable
internal fun TvAccountBoard(
    detail: UserDetailUiState?,
    requests: TvPagedRows<RequestItem>,
    onOpenRequest: (RequestItem) -> Unit,
    onRetry: () -> Unit,
    overlayOpen: Boolean,
    modifier: Modifier = Modifier,
    accountFailed: Boolean = false,
    now: Long = System.currentTimeMillis(),
) {
    val item =
        when (detail) {
            is UserDetailUiState.Ready -> detail.detail.item
            is UserDetailUiState.Seeded -> detail.item
            else -> null
        }
    val quota = (detail as? UserDetailUiState.Ready)?.detail?.quota
    if (item == null) {
        TvAccountUnresolved(failed = accountFailed || detail is UserDetailUiState.Error, onRetry = onRetry, modifier = modifier)
        return
    }
    val restoreFocus = remember { FocusRequester() }
    var restoreRequestId by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(overlayOpen) {
        if (!overlayOpen && restoreRequestId != null) restoreTvOverlayFocus(restoreFocus)
    }
    val rows = (0 until minOf(requests.count, ROW_ITEM_CAP)).mapNotNull { requests.at(it) }
    val verticalInset = dimensionResource(TvR.dimen.tv_overscan_vertical)
    val listState = rememberLazyListState()
    // Which block holds focus. The page is taller than the screen, and the default scroll only brings the focused
    // poster into view, which leaves the title and state beneath it cut off, and leaves the page where it was when
    // focus climbs back to the profile. So the page anchors on this instead.
    var focusArea by rememberSaveable { mutableStateOf(AccountFocusArea.Profile) }
    AnchorAccountScroll(listState, focusArea)
    val startInset = LocalTvContentInset.current + dimensionResource(TvR.dimen.tv_content_gutter_start)
    val endInset = dimensionResource(TvR.dimen.tv_overscan_horizontal)
    // Not [TvBoardFrame]: the requests row runs the full width of the pane, passing under the rail as every row
    // does, so the horizontal insets belong to the blocks above it and to the row itself, not to the page.
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = verticalInset, bottom = verticalInset),
    ) {
        TvScreenHeading(title = stringResource(R.string.tv_rail_account), modifier = Modifier.padding(start = startInset, end = endInset))
        TvStableFocusScroll {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().tvFocusGroup(),
                contentPadding = PaddingValues(vertical = dimensionResource(TvR.dimen.tv_focus_ring_bleed)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
            ) {
                item(key = "profile") {
                    TvProfileCard(
                        item = item,
                        now = now,
                        onFocused = { focusArea = AccountFocusArea.Profile },
                        modifier = Modifier.fillMaxWidth().padding(start = startInset, end = endInset),
                    )
                }
                item(key = "quota") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = startInset, end = endInset),
                        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_stat_tile_gap)),
                    ) {
                        TvQuotaTile(stringResource(R.string.hub_quota_movies), quota?.movie, quota != null, Modifier.weight(1f))
                        TvQuotaTile(stringResource(R.string.hub_quota_tv), quota?.tv, quota != null, Modifier.weight(1f))
                    }
                }
                if (rows.isNotEmpty()) {
                    item(key = "requests") {
                        TvAccountRequestsRow(
                            rows = rows,
                            requestCount = item.requestCount,
                            restoreRequestId = restoreRequestId,
                            restoreFocus = restoreFocus,
                            onFocused = { focusArea = AccountFocusArea.Requests },
                            onOpen = {
                                restoreRequestId = it.id
                                onOpenRequest(it)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvAccountUnresolved(
    failed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (failed) {
        TvMessagePage(
            body = stringResource(R.string.tv_list_load_failed),
            modifier = modifier,
            icon = Icons.Filled.Warning,
            primary = TvPageAction(stringResource(R.string.hub_retry), onRetry),
        )
    } else {
        TvMessagePage(body = stringResource(R.string.tv_loading), modifier = modifier, loading = true)
    }
}

@Composable
private fun AnchorAccountScroll(
    listState: LazyListState,
    focusArea: AccountFocusArea,
) {
    LaunchedEffect(focusArea) {
        when (focusArea) {
            AccountFocusArea.Profile -> listState.animateScrollToItem(0)
            AccountFocusArea.Requests -> {
                if (listState.layoutInfo.visibleItemsInfo.none { it.index == REQUESTS_ITEM_INDEX }) {
                    listState.scrollToItem(REQUESTS_ITEM_INDEX)
                }
                val info = listState.layoutInfo
                val row = info.visibleItemsInfo.firstOrNull { it.index == REQUESTS_ITEM_INDEX } ?: return@LaunchedEffect
                // Down only as far as the whole row, caption included, is in view.
                val overshoot = row.offset + row.size - (info.viewportEndOffset - info.afterContentPadding)
                if (overshoot > 0) listState.animateScrollBy(overshoot.toFloat())
            }
        }
    }
}

@Composable
private fun TvAccountRequestsRow(
    rows: List<RequestItem>,
    requestCount: Int,
    restoreRequestId: Int?,
    restoreFocus: FocusRequester,
    onFocused: () -> Unit,
    onOpen: (RequestItem) -> Unit,
) {
    val heading = stringResource(R.string.tv_account_your_requests)
    TvCardRow(
        items = rows,
        key = { it.id },
        cellWidth = dimensionResource(TvR.dimen.tv_immersive_card_width),
        heading = stringResource(R.string.tv_filter_with_count, heading, requestCount),
        onCellFocused = { onFocused() },
    ) { request, isFocused, onFocusChanged, cellModifier ->
        TvAccountRequestCard(
            request = request,
            isFocused = isFocused,
            onFocusChanged = onFocusChanged,
            onClick = { onOpen(request) },
            cellModifier = if (request.id == restoreRequestId) cellModifier.focusRequester(restoreFocus) else cellModifier,
        )
    }
}

/** One of the user's requests: the poster with its state as an icon chip in the corner, and its title beneath. */
@Composable
private fun TvAccountRequestCard(
    request: RequestItem,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    cellModifier: Modifier,
) {
    val chip = request.statusChip()
    val title = request.title ?: stringResource(request.mediaType.labelRes())
    val status = stringResource(chip.labelRes)
    val tint = chip.tone.tvColor()
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xxs))) {
        TvPosterCard(
            // The state reads out with the title, since the chip is only a glyph.
            title = "$title, $status",
            posterUrl = request.posterUrl,
            isFocused = isFocused,
            onFocusChanged = onFocusChanged,
            enabled = true,
            onClick = onClick,
            modifier = cellModifier,
            badge = { TvPosterIconChip(icon = chip.labelRes.statusIcon(), tint = tint) },
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            // Two lines always, so a row of cards keeps one height whatever the titles are.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(dimensionResource(TvR.dimen.tv_immersive_card_width)),
        )
    }
}

/** A request state's glyph for the poster chip, from the label the status chip carries. */
private fun Int.statusIcon(): ImageVector =
    when (this) {
        R.string.request_state_declined -> Icons.Filled.Close
        R.string.request_state_failed -> Icons.Filled.ErrorOutline
        R.string.media_state_available -> Icons.Filled.CheckCircle
        R.string.media_state_partially_available -> Icons.Filled.Done
        R.string.media_state_processing -> Icons.Filled.Download
        R.string.request_state_queued -> Icons.Filled.Schedule
        R.string.request_state_approved -> Icons.Filled.Check
        R.string.request_state_pending -> Icons.Filled.HourglassEmpty
        else -> Icons.Filled.Block
    }
