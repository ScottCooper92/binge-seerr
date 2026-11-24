package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.binge.designsystem.template.DetailState
import com.binge.designsystem.template.MediaDetailCallbacks
import com.binge.designsystem.template.MovieDetailScreen
import com.binge.designsystem.template.readingMargin
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.title.TitleCompany
import com.cooper.binge.companion.seerr.discover.title.TitleKeyword
import com.cooper.binge.companion.seerr.discover.title.trailerUrl
import com.cooper.binge.companion.seerr.ui.discover.links.ExternalLinksSection
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheet
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetTarget
import com.cooper.binge.companion.seerr.ui.discover.request.messageRes
import com.cooper.binge.companion.seerr.ui.requests.SiblingSheet
import com.cooper.binge.companion.seerr.ui.state.messageRes
import com.cooper.binge.companion.seerr.ui.state.rememberErrorReporter
import kotlinx.coroutines.flow.Flow

class TitlePageActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onRequest: () -> Unit,
    val onCloseRequest: () -> Unit,
    val onRequested: (com.cooper.binge.companion.seerr.discover.request.RequestResult) -> Unit,
    val onOpenTitle: (TitleResult) -> Unit,
    val onOpenPerson: (Int) -> Unit,
    val onOpenCollection: ((Int) -> Unit)?,
    val onOpenUrl: (String) -> Unit,
    val onOpenCredits: () -> Unit,
    val onOpenCompany: ((TitleCompany) -> Unit)? = null,
    val onOpenKeyword: ((TitleKeyword) -> Unit)? = null,
    /** Reads the page again where it stands, for the snackbar after a request whose reload failed. */
    val onReload: () -> Unit = {},
    /** The sheet of a request the primary opened, closed. */
    val onCloseManage: () -> Unit = {},
    /** The status sheet the primary opens where there is no request to manage, closed. */
    val onCloseStatus: () -> Unit = {},
    /** Request in 4K, tapped in a request's sheet: that sheet closes and the request sheet opens. */
    val onRequestFourK: () -> Unit = {},
    /** Request the remaining seasons of a partly available series, tapped in a sheet: that sheet gives way to the request sheet. */
    val onRequestRemaining: () -> Unit = {},
    /** Watch in Binge: the hand-off to the title in Binge, or null where Binge is not there to answer it. */
    val onWatchInBinge: (() -> Unit)? = null,
    /** The watchlist button, tapped. */
    val onToggleWatchlist: () -> Unit = {},
    /** Block, asked and answered; null where this viewer may not block. */
    val block: TitleBlock? = null,
    /** Remove from blocklist, tapped in the status sheet of a blocklisted title. */
    val onUnblock: () -> Unit = {},
    /** The sheet of one request, backed by that request's own view model, which the nav entry supplies. */
    val manageSheet: @Composable (SiblingSheet) -> Unit = {},
    /** Report, drawn once the title is in the library; null where the page has nothing to report on. */
    val onReport: (() -> Unit)? = null,
    /** The report sheet, closed. */
    val onCloseReport: () -> Unit = {},
    /** The report form for this title, hosted over the page. */
    val reportSheet: @Composable (onClose: () -> Unit) -> Unit = {},
    /** The list of requests, opened from a row of the title's sheet where there are several, and closed. */
    val onOpenRequests: () -> Unit = {},
    val onCloseRequests: () -> Unit = {},
    /** Back from the requests list to the title's own sheet. */
    val onBackFromRequests: () -> Unit = {},
    /** Back from a request's sheet to the list it was picked from. */
    val onBackToRequests: () -> Unit = {},
    /** A request picked from that list: its sheet opens. */
    val onPickRequest: (Int) -> Unit = {},
    /** The services sheet's action in the page's round actions; null where the title has no request or link to show. */
    val onOpenServiceInfo: (() -> Unit)? = null,
    val onCloseServiceInfo: () -> Unit = {},
)

/** The film page: the design system's movie template, filled with this app's primary action, sheets and sections. */
@Composable
internal fun TitlePageScreen(
    state: TitlePageUiState,
    events: Flow<TitlePageEvent>,
    actions: TitlePageActions,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    when (state) {
        TitlePageUiState.Loading ->
            MovieDetailScreen(
                state = DetailState.Loading(stats = LOADING_STATS, roundActions = ROUND_SLOTS, hasPrimary = true, rails = 1),
                callbacks = MediaDetailCallbacks(onBack = actions.onBack),
                modifier = modifier,
            )
        is TitlePageUiState.Error -> ErrorPage(state, actions, modifier)
        is TitlePageUiState.Ready -> ReadyPage(state, events, actions, modifier, scrollState)
    }
}

/** What the loaded page is expected to show before it arrives: a rating, a year and a runtime. */
private const val LOADING_STATS = 3

@Composable
private fun ErrorPage(
    state: TitlePageUiState.Error,
    actions: TitlePageActions,
    modifier: Modifier,
) {
    MovieDetailScreen(
        state = titleFailedState(state.error, actions.onRetry),
        callbacks = MediaDetailCallbacks(onBack = actions.onBack, onRetry = actions.onRetry),
        modifier = modifier,
    )
}

@Composable
private fun ReadyPage(
    state: TitlePageUiState.Ready,
    events: Flow<TitlePageEvent>,
    actions: TitlePageActions,
    modifier: Modifier,
    scrollState: ScrollState,
) {
    val snackbar = remember { SnackbarHostState() }
    val reporter = rememberErrorReporter(snackbar)
    val resources = LocalContext.current.resources
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is TitlePageEvent.Requested -> snackbar.showSnackbar(resources.getString(event.result.messageRes()))
                TitlePageEvent.Blocked -> snackbar.showSnackbar(resources.getString(R.string.request_title_blocked))
                is TitlePageEvent.BlockFailed -> reporter.report(event.error)
                TitlePageEvent.Unblocked -> snackbar.showSnackbar(resources.getString(R.string.blocklist_removed))
                is TitlePageEvent.WatchlistFailed -> reporter.report(event.error)
                is TitlePageEvent.UnblockFailed -> reporter.report(event.error)
                TitlePageEvent.ReloadFailed -> {
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
    val movie = state.movie
    val model = movieDetailModel(state, actions)
    val servicesState = rememberServicesState(movie.requests, TitleKind.Movie, movie.tmdbId)
    if (!servicesState.settled) {
        MovieDetailScreen(
            state = DetailState.Loading(stats = LOADING_STATS, roundActions = ROUND_SLOTS, hasPrimary = true, rails = 1),
            callbacks = MediaDetailCallbacks(onBack = actions.onBack),
            modifier = modifier,
        )
        return
    }
    val servicesContent = servicesState.groups
    val actionsModel = titleActionsModel(movie.standard, movie.fourK, movie.requests, movie.mayReport, movie.mayBlock)
    val slots =
        rememberTitlePageSlots(
            TitleChromeFacts(
                title = movie.title,
                webUrl = movie.webUrl,
                kind = TitleKind.Movie,
                tmdbId = movie.tmdbId,
                watchUrl = movie.watchUrl,
                mediaServerName = movie.mediaServerName,
                mediaServer = movie.mediaServer,
                trailerUrl = movie.videos.trailerUrl(),
                onWatchlist = movie.onWatchlist,
                mayWatchlist = movie.mayWatchlist,
                mayBlock = movie.mayBlock,
                moreToRequest = movie.fourK?.mayRequest == true,
                requestCount = movie.requests.size,
            ),
            actionsModel,
            servicesContent,
            actions,
        )
    MovieDetailScreen(
        state = DetailState.Content(model.detail),
        callbacks = model.callbacks(actions),
        modifier = modifier,
        primary = slots.primary,
        roundActions = slots.roundActions,
        topBarActions = slots.topBarActions,
        afterPeople = { WhereToWatchSection(movie.providers, actions.onOpenUrl, Modifier.readingMargin()) },
        afterRails = { ExternalLinksSection(movie.links, actions.onOpenUrl) },
        // The template's column puts its gap before this empty slot: the page's bottom padding under the Details, as it had.
        atEnd = if (model.detail.info != null) ({ Spacer(Modifier) }) else null,
        overlays = {
            MovieOverlays(state, actions, snackbar)
            slots.overlays()
        },
        snackbarHostState = snackbar,
        scrollState = scrollState,
    )
}

@Composable
private fun MovieOverlays(
    state: TitlePageUiState.Ready,
    actions: TitlePageActions,
    snackbar: SnackbarHostState,
) {
    val movie = state.movie
    if (state.requestSheetOpen) {
        RequestSheet(
            target =
                RequestSheetTarget(
                    kind = com.cooper.binge.companion.seerr.discover.TitleKind.Movie,
                    tmdbId = movie.tmdbId,
                    title = movie.title,
                    standardOpen = movie.standard.mayRequest && !state.requestSheetFourK,
                    fourKOpen = movie.fourK?.mayRequest == true,
                    posterUrl = movie.posterUrl,
                    meta = movie.requestMeta(),
                ),
            onDismiss = actions.onCloseRequest,
            onResult = actions.onRequested,
        )
    }
    if (state.statusSheetOpen) {
        TitleStatusSheet(
            state = titleActionsModel(movie.standard, movie.fourK, movie.requests, movie.mayReport).primary.state,
            canRequest = movie.canRequest,
            fourKOpen = movie.fourK?.mayRequest == true,
            remainingOpen = false,
            onRequestFourK = actions.onRequestFourK,
            onRequestRemaining = actions.onRequestRemaining,
            requestCount = movie.requests.size,
            header = TitleSheetHeaderFacts(movie.title, movie.posterUrl),
            onOpenRequests = actions.onOpenRequests,
            onDismiss = actions.onCloseStatus,
            onUnblock = actions.onUnblock.takeIf { movie.mayUnblock },
        )
    }
    if (state.reportSheetOpen) actions.reportSheet(actions.onCloseReport)
    if (state.blockConfirmOpen) actions.block?.let { TitleBlockConfirm(movie.title, it) }
    state.manageRequestId?.let { requestId ->
        TitleManageSheet(requestId, state.manageSheetOpen, snackbar, movie.sheetFacts(), actions, state.manageFromList, state.manageScope)
    }
    val servicesContent = rememberServicesContent(movie.requests, TitleKind.Movie, movie.tmdbId)
    TitleRequestsAndServices(
        state.requestsSheetOpen,
        state.serviceSheetOpen,
        movie.requests,
        servicesContent,
        movie.mediaServerName,
        actions,
    )
}
