package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.focus.TvArrivalFocus
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.TvOverlayCloser
import com.binge.designsystem.tv.focus.TvStableFocusScroll
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.rememberTvOverlayCloser
import com.binge.designsystem.tv.focus.tvArrivalTarget
import com.binge.designsystem.tv.theme.TvButtonStyle
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.DetailDownload
import io.github.scottcooper92.binge.seerr.ui.requests.ModerationEvent
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetail
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailUiState
import io.github.scottcooper92.binge.seerr.ui.requests.SeasonState
import io.github.scottcooper92.binge.seerr.ui.requests.isError
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes
import io.github.scottcooper92.binge.seerr.ui.requests.messageRes
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
import io.github.scottcooper92.binge.seerr.ui.tv.TvBoardPlate
import io.github.scottcooper92.binge.seerr.ui.tv.TvFormNote
import io.github.scottcooper92.binge.seerr.ui.tv.TvFormNoteTone
import io.github.scottcooper92.binge.seerr.ui.tv.TvLoadingPlate
import io.github.scottcooper92.binge.seerr.ui.tv.rememberTvTransientEvent
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/** Everything the TV request detail page can ask of its host, in one place so the overlay stays a wiring. */
internal class TvRequestDetailActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    /** Null hides the affordance — Binge is not expected to answer, and there is no browser to fall back to. */
    val onOpenInBinge: (() -> Unit)?,
    val onApprove: () -> Unit,
    val onRetryRequest: () -> Unit,
    val onDecline: (Boolean) -> Unit,
    val onRemove: (Boolean) -> Unit,
)

/**
 * One request as a read-only television page: the title over its state, who asked and when, the seasons
 * and their status, and what is downloading now. The same [RequestDetailUiState] the phone's
 * `RequestDetailScreen` renders — this is a TV surface over the same ViewModel, so a moderation elsewhere
 * (or this page's own) shows here without any polling. Presented as a full-screen overlay above the rail,
 * so it owns Back itself rather than leaving it to the shell.
 */
@Composable
internal fun TvRequestDetailScreen(
    state: RequestDetailUiState,
    events: Flow<ModerationEvent>,
    actions: TvRequestDetailActions,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = actions.onBack)
    // A removed request has no page to stay on.
    LaunchedEffect(events) {
        events.collect { if (it.removesTheRequest) actions.onBack() }
    }
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state) {
            RequestDetailUiState.Loading -> TvLoadingPlate(modifier = Modifier.fillMaxSize())
            is RequestDetailUiState.Error ->
                TvBoardPlate(
                    body = stringResource(state.error.messageRes()),
                    icon = Icons.Filled.Warning,
                    primary = stringResource(R.string.hub_retry) to actions.onRetry,
                    modifier = Modifier.fillMaxSize(),
                )
            is RequestDetailUiState.Ready -> TvRequestDetailContent(detail = state.detail, events = events, actions = actions)
        }
    }
}

/**
 * The loaded page's content: a fixed-width reading column so a title's meta line does not stretch across a
 * ten-foot screen. Arrival focus lands on the first focusable thing in reading order — a season, a
 * download, the primary button, or (failing all three) Open in Binge — and if the request offers none of
 * those either, the reading column itself takes focus so the page is never left with nowhere for the D-pad
 * to land.
 */
@Composable
private fun TvRequestDetailContent(
    detail: RequestDetail,
    events: Flow<ModerationEvent>,
    actions: TvRequestDetailActions,
) {
    var acting by rememberSaveable { mutableStateOf(false) }
    val event = rememberTvTransientEvent(events)
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    val manageFocus = remember { FocusRequester() }
    val closer = rememberTvOverlayCloser(restoreTo = manageFocus, onClose = { acting = false })
    val onFirst = firstFocusableSection(detail, hasOpenInBinge = actions.onOpenInBinge != null)

    TvStableFocusScroll {
        Column(
            modifier =
                Modifier
                    .width(dimensionResource(R.dimen.tv_detail_content_width))
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = dimensionResource(TvR.dimen.tv_overscan_horizontal),
                        vertical = dimensionResource(TvR.dimen.tv_overscan_vertical),
                    ).let {
                        if (onFirst == TvDetailSection.Content) it.tvArrivalTarget(arrival).focusable() else it
                    },
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_detail_section_gap)),
        ) {
            val item = detail.item
            Text(
                text = item.title ?: stringResource(item.mediaType.labelRes()),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TvRequestDetailFacts(item)
            TvSeasonsSection(detail.seasons, arrivalOnFirst = onFirst == TvDetailSection.Seasons, arrival = arrival)
            TvDownloadsSection(detail.downloads, arrivalOnFirst = onFirst == TvDetailSection.Downloads, arrival = arrival)
            TvRequestDetailButtons(
                detail = detail,
                actions = actions,
                onManage = { acting = true },
                manageFocus = manageFocus,
                arrivalOnManage = onFirst == TvDetailSection.Manage,
                arrivalOnOpen = onFirst == TvDetailSection.OpenInBinge,
                arrival = arrival,
            )
            event?.let {
                TvFormNote(
                    text = stringResource(it.messageRes()),
                    tone = if (it.isError()) TvFormNoteTone.Error else TvFormNoteTone.Success,
                )
            }
        }
    }
    if (acting) TvRequestDetailSheet(detail = detail, actions = actions, closer = closer)
}

/**
 * In reading order, whichever renders first. [Content] is the last resort, for a request with no seasons,
 * no downloads, no moderation this viewer can do, and no Open in Binge to hand off to — otherwise arrival
 * has nothing to offer focus to, and the page's D-pad silently goes dead (the read-only case: an
 * already-available title with no active downloads, viewed by someone with no approve/decline/retry/remove
 * permission).
 */
private enum class TvDetailSection { Seasons, Downloads, Manage, OpenInBinge, Content }

private fun firstFocusableSection(
    detail: RequestDetail,
    hasOpenInBinge: Boolean,
): TvDetailSection =
    when {
        detail.seasons.isNotEmpty() -> TvDetailSection.Seasons
        detail.downloads.isNotEmpty() -> TvDetailSection.Downloads
        detail.actions.any -> TvDetailSection.Manage
        hasOpenInBinge -> TvDetailSection.OpenInBinge
        else -> TvDetailSection.Content
    }

@Composable
private fun ColumnScope.TvSeasonsSection(
    seasons: List<SeasonState>,
    arrivalOnFirst: Boolean,
    arrival: TvArrivalFocus,
) {
    if (seasons.isEmpty()) return
    TvDetailSectionHeader(stringResource(R.string.request_seasons))
    seasons.forEachIndexed { index, season ->
        TvSeasonRow(season, modifier = if (arrivalOnFirst && index == 0) Modifier.tvArrivalTarget(arrival) else Modifier)
    }
}

@Composable
private fun ColumnScope.TvDownloadsSection(
    downloads: List<DetailDownload>,
    arrivalOnFirst: Boolean,
    arrival: TvArrivalFocus,
) {
    if (downloads.isEmpty()) return
    TvDetailSectionHeader(stringResource(R.string.request_downloads))
    downloads.forEachIndexed { index, download ->
        TvDownloadRow(download, modifier = if (arrivalOnFirst && index == 0) Modifier.tvArrivalTarget(arrival) else Modifier)
    }
}

/** The row's moderation, moved onto the page: the same end-edge sheet the board used to open directly. */
@Composable
private fun TvRequestDetailSheet(
    detail: RequestDetail,
    actions: TvRequestDetailActions,
    closer: TvOverlayCloser,
) {
    TvRequestActionsSheet(
        item = detail.item,
        actions = detail.actions,
        sheetActions =
            TvRequestSheetActions(
                onApprove = {
                    actions.onApprove()
                    closer.close()
                },
                onRetry = {
                    actions.onRetryRequest()
                    closer.close()
                },
                onDecline = { block ->
                    actions.onDecline(block)
                    closer.close()
                },
                onRemove = { block ->
                    actions.onRemove(block)
                    closer.close()
                },
                onDismiss = closer::close,
            ),
    )
}

/**
 * The one moderation action this viewer has, opening the same end-edge sheet the board used to open
 * directly — and Open in Binge, the one hand-off this read-only page makes. Stacked rather than side by
 * side, so ↓ off the last row above always lands on the first button rather than whichever sits nearer.
 */
@Composable
private fun TvRequestDetailButtons(
    detail: RequestDetail,
    actions: TvRequestDetailActions,
    onManage: () -> Unit,
    manageFocus: FocusRequester,
    arrivalOnManage: Boolean,
    arrivalOnOpen: Boolean,
    arrival: TvArrivalFocus,
) {
    val reviewable = detail.actions.canApprove || detail.actions.canRetry
    val manageVisible = detail.actions.any
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        if (manageVisible) {
            TvButton(
                label = stringResource(if (reviewable) R.string.request_primary_review else R.string.request_primary_manage),
                onClick = onManage,
                style = TvButtonStyle.Primary,
                modifier = Modifier.focusRequester(manageFocus).let { if (arrivalOnManage) it.tvArrivalTarget(arrival) else it },
            )
        }
        actions.onOpenInBinge?.let { onOpen ->
            TvButton(
                label = stringResource(R.string.request_open_binge),
                onClick = onOpen,
                modifier = if (arrivalOnOpen) Modifier.tvArrivalTarget(arrival) else Modifier,
            )
        }
    }
}
