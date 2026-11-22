package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.binge.designsystem.template.DetailState
import com.binge.designsystem.template.MediaDetailCallbacks
import com.binge.designsystem.template.SeriesDetailCallbacks
import com.binge.designsystem.template.SeriesDetailScreen
import com.binge.designsystem.template.readingMargin
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.title.trailerUrl
import com.cooper.binge.companion.seerr.ui.discover.links.ExternalLinksSection
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheet
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetTarget
import com.cooper.binge.companion.seerr.ui.discover.request.messageRes
import com.cooper.binge.companion.seerr.ui.state.messageRes
import com.cooper.binge.companion.seerr.ui.state.rememberErrorReporter
import kotlinx.coroutines.flow.Flow

/** The series page: the design system's series template, filled with this app's primary action, sheets and sections. */
@Composable
internal fun SeriesPageScreen(
    state: SeriesPageUiState,
    events: Flow<SeriesPageEvent>,
    actions: TitlePageActions,
    seasonActions: SeasonActions,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    when (state) {
        SeriesPageUiState.Loading ->
            SeriesDetailScreen(
                state = DetailState.Loading(stats = LOADING_STATS, hasPrimary = true, rails = 1),
                series = null,
                callbacks = MediaDetailCallbacks(onBack = actions.onBack),
                seriesCallbacks = SeriesDetailCallbacks(onSelectSeason = {}),
                modifier = modifier,
            )
        is SeriesPageUiState.Error -> ErrorPage(state, actions, modifier)
        is SeriesPageUiState.Ready -> ReadySeriesPage(state, events, actions, seasonActions, modifier, scrollState)
    }
}

@Composable
private fun ReadySeriesPage(
    state: SeriesPageUiState.Ready,
    events: Flow<SeriesPageEvent>,
    actions: TitlePageActions,
    seasonActions: SeasonActions,
    modifier: Modifier,
    scrollState: ScrollState,
) {
    val snackbar = remember { SnackbarHostState() }
    val reporter = rememberErrorReporter(snackbar)
    val resources = LocalContext.current.resources
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is SeriesPageEvent.Requested -> snackbar.showSnackbar(resources.getString(event.result.messageRes()))
                SeriesPageEvent.Blocked -> snackbar.showSnackbar(resources.getString(R.string.request_title_blocked))
                is SeriesPageEvent.BlockFailed -> reporter.report(event.error)
                SeriesPageEvent.Unblocked -> snackbar.showSnackbar(resources.getString(R.string.blocklist_removed))
                is SeriesPageEvent.WatchlistFailed -> reporter.report(event.error)
                is SeriesPageEvent.UnblockFailed -> reporter.report(event.error)
                SeriesPageEvent.ReloadFailed -> {
                    val answer =
                        snackbar.showSnackbar(
                            message = resources.getString(R.string.title_page_reload_failed),
                            actionLabel = resources.getString(R.string.action_try_again),
                            duration = SnackbarDuration.Long,
                        )
                    if (answer == SnackbarResult.ActionPerformed) actions.onReload()
                }
            }
        }
    }
    val series = state.series
    val context = LocalContext.current
    val model = seriesDetailModel(state, actions)
    val episodes = seriesEpisodes(state)
    val actionsModel = titleActionsModel(series.standard, series.fourK, series.requests, series.mayReport, series.mayBlock)
    SeriesDetailScreen(
        state = DetailState.Content(model.detail),
        series = episodes,
        callbacks = model.callbacks(actions),
        seriesCallbacks = seasonActions.callbacks(state.selectedSeason, series.name),
        modifier = modifier,
        primary = { primaryModifier -> PrimaryAction(actionsModel.primary, actions.onRequest, primaryModifier) },
        roundActions =
            titleRoundActions(
                model = actionsModel,
                onTrailer = series.videos.trailerUrl()?.let { url -> { actions.onOpenUrl(url) } },
                watchlist = WatchlistButton(series.onWatchlist, actions.onToggleWatchlist).takeIf { series.mayWatchlist },
                onReport = actions.onReport,
                onBlock = actions.block?.onAsk.takeIf { series.mayBlock },
            ),
        topBarActions = { glassAlpha -> ShareBarAction(glassAlpha) { context.shareTitle(series.name, series.webUrl) } },
        afterPeople = { WhereToWatchSection(series.providers, actions.onOpenUrl, Modifier.readingMargin()) },
        afterRails = { ExternalLinksSection(series.links, actions.onOpenUrl) },
        // The template's column puts its gap before this empty slot: the page's bottom padding under the Details, as it had.
        atEnd = if (model.detail.info != null) ({ Spacer(Modifier) }) else null,
        overlays = { SeriesOverlays(state, actions, snackbar) },
        snackbarHostState = snackbar,
        scrollState = scrollState,
    )
}

@Composable
private fun SeriesOverlays(
    state: SeriesPageUiState.Ready,
    actions: TitlePageActions,
    snackbar: SnackbarHostState,
) {
    val series = state.series
    if (state.requestSheetOpen) {
        RequestSheet(
            target =
                RequestSheetTarget(
                    kind = TitleKind.Series,
                    tmdbId = series.tmdbId,
                    title = series.name,
                    standardOpen = series.standard.mayRequest && !state.requestSheetFourK,
                    fourKOpen = series.fourK?.mayRequest == true,
                    seasonChoices = series.seasonChoices,
                    partialRequests = series.partialRequests,
                    posterUrl = series.posterUrl,
                    meta = series.requestMeta(),
                ),
            onDismiss = actions.onCloseRequest,
            onResult = actions.onRequested,
        )
    }
    if (state.statusSheetOpen) {
        TitleStatusSheet(
            state =
                titleActionsModel(
                    state.series.standard,
                    state.series.fourK,
                    state.series.requests,
                    state.series.mayReport,
                ).primary.state,
            watchUrl = state.series.watchUrl,
            mediaServerName = state.series.mediaServerName,
            canRequest = state.series.canRequest,
            onWatch = actions.onOpenUrl,
            onWatchInBinge = actions.onWatchInBinge,
            fourKOpen = state.series.fourK?.mayRequest == true,
            remainingOpen = state.series.standard.mayRequest,
            onRequestFourK = actions.onRequestFourK,
            onRequestRemaining = actions.onRequestRemaining,
            onDismiss = actions.onCloseStatus,
            onUnblock = actions.onUnblock.takeIf { state.series.mayUnblock },
        )
    }
    if (state.reportSheetOpen) actions.reportSheet(actions.onCloseReport)
    if (state.blockConfirmOpen) actions.block?.let { TitleBlockConfirm(state.series.name, it) }
    state.manageRequestId?.let { requestId ->
        TitleManageSheet(requestId, state.manageSheetOpen, snackbar, series.sheetFacts(), actions)
    }
}

/** What the loaded page is expected to show before it arrives: a rating, a year and the episodes or the certification. */
private const val LOADING_STATS = 3

@Composable
private fun ErrorPage(
    state: SeriesPageUiState.Error,
    actions: TitlePageActions,
    modifier: Modifier,
) {
    SeriesDetailScreen(
        state = titleFailedState(state.error, actions.onRetry),
        series = null,
        callbacks = MediaDetailCallbacks(onBack = actions.onBack, onRetry = actions.onRetry),
        seriesCallbacks = SeriesDetailCallbacks(onSelectSeason = {}),
        modifier = modifier,
    )
}
