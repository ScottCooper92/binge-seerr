package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.title.TitleCompany
import com.cooper.binge.companion.seerr.discover.title.TitleKeyword
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_MOVIE
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_TV
import com.cooper.binge.companion.seerr.ui.SiblingRequestSheet
import com.cooper.binge.companion.seerr.ui.bingeTitleHandOff
import com.cooper.binge.companion.seerr.ui.openInBrowser
import com.cooper.binge.companion.seerr.ui.requests.toRequestMediaTypeOrNull
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import com.cooper.homelab.core.flags.bingePromotion
import com.cooper.homelab.core.uiandroid.flags.LocalFlags

/**
 * The page of one title: a film's or a series'. A person and a collection open their own routes, so they never reach here
 * and fall to the empty branch.
 */
@Composable
internal fun TitleDetailEntry(
    kind: TitleKind,
    tmdbId: Int,
    openRequest: Int?,
    onBack: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenSeason: (seasonNumber: Int, seriesName: String) -> Unit,
    onOpenCompany: (TitleCompany) -> Unit,
    onOpenKeyword: (TitleKeyword) -> Unit,
    onOpenUser: (Int) -> Unit,
    onOpenServices: () -> Unit,
) {
    when (kind) {
        TitleKind.Movie ->
            FilmPageEntry(
                tmdbId,
                openRequest,
                onBack,
                onOpenCredits,
                onOpenCompany,
                onOpenKeyword,
                onOpenUser,
                onOpenServices,
            )
        TitleKind.Series ->
            SeriesPageEntry(
                tmdbId,
                openRequest,
                onBack,
                onOpenCredits,
                onOpenSeason,
                onOpenCompany,
                onOpenKeyword,
                onOpenUser,
                onOpenServices,
            )
        else -> EmptyScreen(message = "")
    }
}

@Composable
private fun FilmPageEntry(
    tmdbId: Int,
    openRequest: Int?,
    onBack: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenCompany: (TitleCompany) -> Unit,
    onOpenKeyword: (TitleKeyword) -> Unit,
    onOpenUser: (Int) -> Unit,
    onOpenServices: () -> Unit,
) {
    val viewModel = hiltViewModel<TitlePageViewModel, TitlePageViewModel.Factory>(creationCallback = { it.create(tmdbId) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OpenRequestOnce(openRequest, state is TitlePageUiState.Ready) { viewModel.openRequest(it) }
    TitlePageScreen(
        state = state,
        events = viewModel.events,
        actions =
            rememberTitlePageActions(
                onBack = onBack,
                onOpenCredits = onOpenCredits,
                onOpenCompany = onOpenCompany,
                onOpenKeyword = onOpenKeyword,
                onRetry = viewModel::load,
                onRequest = viewModel::onPrimary,
                requestsSheet =
                    RequestsSheetWiring({
                        viewModel.setRequestsSheetOpen(true)
                    }, {
                        viewModel.setRequestsSheetOpen(false)
                    }, {
                        viewModel.backToRequests()
                    }, viewModel::pickRequest, viewModel::backToRequests, {
                        viewModel.setServiceSheetOpen(
                            true,
                        )
                    }, { viewModel.setServiceSheetOpen(false) }),
                request = TitleRequestActions(viewModel::closeRequestSheet, viewModel::requested, viewModel::reload),
                manage =
                    TitleManage(
                        { viewModel.closeSheet(TitleSheet.Manage) },
                        { viewModel.closeSheet(TitleSheet.Status) },
                        { viewModel.requestFromSheet(true) },
                        { viewModel.requestFromSheet(false) },
                        { viewModel.blocklist(BlocklistStep.Unblock) },
                        TitleBlock(
                            { viewModel.blocklist(BlocklistStep.Ask) },
                            { viewModel.blocklist(BlocklistStep.Block) },
                            { viewModel.blocklist(BlocklistStep.Cancel) },
                        ),
                        onOpenUser,
                        onOpenServices,
                    ),
                side =
                    TitleSideActions(
                        SEERR_MEDIA_TYPE_MOVIE,
                        tmdbId,
                        { viewModel.setReportSheetOpen(true) },
                        { viewModel.setReportSheetOpen(false) },
                        viewModel::toggleWatchlist,
                    ),
            ),
    )
}

@Composable
private fun SeriesPageEntry(
    tmdbId: Int,
    openRequest: Int?,
    onBack: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenSeason: (Int, String) -> Unit,
    onOpenCompany: (TitleCompany) -> Unit,
    onOpenKeyword: (TitleKeyword) -> Unit,
    onOpenUser: (Int) -> Unit,
    onOpenServices: () -> Unit,
) {
    val viewModel = hiltViewModel<SeriesPageViewModel, SeriesPageViewModel.Factory>(creationCallback = { it.create(tmdbId) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OpenRequestOnce(openRequest, state is SeriesPageUiState.Ready) { viewModel.openRequest(it) }
    SeriesPageScreen(
        state = state,
        events = viewModel.events,
        actions =
            rememberTitlePageActions(
                onBack = onBack,
                onOpenCredits = onOpenCredits,
                onOpenCompany = onOpenCompany,
                onOpenKeyword = onOpenKeyword,
                onRetry = viewModel::load,
                onRequest = viewModel::onPrimary,
                requestsSheet =
                    RequestsSheetWiring({
                        viewModel.setRequestsSheetOpen(true)
                    }, {
                        viewModel.setRequestsSheetOpen(false)
                    }, {
                        viewModel.backToRequests()
                    }, viewModel::pickRequest, viewModel::backToRequests, {
                        viewModel.setServiceSheetOpen(
                            true,
                        )
                    }, { viewModel.setServiceSheetOpen(false) }),
                request = TitleRequestActions(viewModel::closeRequestSheet, viewModel::requested, viewModel::reload),
                manage =
                    TitleManage(
                        { viewModel.closeSheet(TitleSheet.Manage) },
                        { viewModel.closeSheet(TitleSheet.Status) },
                        { viewModel.requestFromSheet(true) },
                        { viewModel.requestFromSheet(false) },
                        { viewModel.blocklist(BlocklistStep.Unblock) },
                        TitleBlock(
                            { viewModel.blocklist(BlocklistStep.Ask) },
                            { viewModel.blocklist(BlocklistStep.Block) },
                            { viewModel.blocklist(BlocklistStep.Cancel) },
                        ),
                        onOpenUser,
                        onOpenServices,
                    ),
                side =
                    TitleSideActions(
                        SEERR_MEDIA_TYPE_TV,
                        tmdbId,
                        { viewModel.setReportSheetOpen(true) },
                        { viewModel.setReportSheetOpen(false) },
                        viewModel::toggleWatchlist,
                    ),
            ),
        seasonActions = SeasonActions(viewModel::selectSeason, viewModel::retryEpisodes, onOpenSeason),
    )
}

/** The page's actions with the links a title page shares: another title, a web page. A studio or network opens its grid; a person and a collection open their own pages, which gives the film's collection rail its See all. */
@Composable
private fun rememberTitlePageActions(
    onBack: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenCompany: (TitleCompany) -> Unit,
    onOpenKeyword: (TitleKeyword) -> Unit,
    onRetry: () -> Unit,
    onRequest: () -> Unit,
    requestsSheet: RequestsSheetWiring,
    request: TitleRequestActions,
    manage: TitleManage,
    side: TitleSideActions,
): TitlePageActions {
    val context = LocalContext.current
    val openTitle = LocalOpenTitle.current
    val promoted = LocalFlags.current.bingePromotion
    val watchInBinge =
        remember(context, promoted, side.mediaType, side.tmdbId) {
            side.mediaType.toRequestMediaTypeOrNull()?.let { context.bingeTitleHandOff(promoted, it, side.tmdbId) }
        }
    return TitlePageActions(
        onBack = onBack,
        onRetry = onRetry,
        onRequest = onRequest,
        onCloseRequest = request.onClose,
        onRequested = request.onRequested,
        onOpenTitle = { result: TitleResult -> openTitle(result.kind, result.tmdbId, "") },
        onOpenPerson = { openTitle(TitleKind.Person, it, "") },
        onOpenCollection = { id -> openTitle(TitleKind.Collection, id, "") },
        onOpenUrl = context::openInBrowser,
        onOpenCredits = onOpenCredits,
        onOpenCompany = onOpenCompany,
        onOpenKeyword = onOpenKeyword,
        onReload = request.onReload,
        onCloseManage = manage.onClose,
        onCloseStatus = manage.onCloseStatus,
        onRequestFourK = manage.onRequestFourK,
        onRequestRemaining = manage.onRequestRemaining,
        onUnblock = manage.onUnblock,
        block = manage.block,
        manageSheet = { sheet -> SiblingRequestSheet(sheet, manage.onOpenUser, manage.onOpenServices) },
        onReport = side.onOpen,
        onCloseReport = side.onClose,
        onOpenRequests = requestsSheet.onOpenRequests,
        onCloseRequests = requestsSheet.onCloseRequests,
        onBackFromRequests = requestsSheet.onBackFromRequests,
        onPickRequest = requestsSheet.onPick,
        onBackToRequests = requestsSheet.onBackToRequests,
        onOpenServiceInfo = requestsSheet.onOpenServices,
        onCloseServiceInfo = requestsSheet.onCloseServices,
        onWatchInBinge = watchInBinge,
        onToggleWatchlist = side.onToggleWatchlist,
        reportSheet = { onClose -> TitleReportSheet(side.mediaType, side.tmdbId, onClose) },
    )
}

/** How the page's request sheet closes, what it does with a request made, and how the page reloads after one. */
private class TitleRequestActions(
    val onClose: () -> Unit,
    val onRequested: (RequestResult) -> Unit,
    val onReload: () -> Unit,
)

/** What the page needs for the sheets over it: how the request's and the status sheet close, and where a requester and the services open. */
private class TitleManage(
    val onClose: () -> Unit,
    val onCloseStatus: () -> Unit,
    val onRequestFourK: () -> Unit,
    val onRequestRemaining: () -> Unit,
    val onUnblock: () -> Unit,
    val block: TitleBlock,
    val onOpenUser: (Int) -> Unit,
    val onOpenServices: () -> Unit,
)

/** What the page needs beside its sheets: which title a report is for, how to open and close the form, and the watchlist toggle. */
private class TitleSideActions(
    val mediaType: String,
    val tmdbId: Int,
    val onOpen: () -> Unit,
    val onClose: () -> Unit,
    val onToggleWatchlist: () -> Unit,
)

/** Opens the request [id] asks for once the page has loaded, and not again after a rotation. */
@Composable
private fun OpenRequestOnce(
    id: Int?,
    ready: Boolean,
    open: (Int) -> Unit,
) {
    var done by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ready) {
        if (id != null && ready && !done) {
            done = true
            open(id)
        }
    }
}

/** What the page needs for the requests list and the services sheet: how they open and close, and which request the list picks. */
private class RequestsSheetWiring(
    val onOpenRequests: () -> Unit,
    val onCloseRequests: () -> Unit,
    val onBackFromRequests: () -> Unit,
    val onPick: (Int) -> Unit,
    val onBackToRequests: () -> Unit,
    val onOpenServices: () -> Unit,
    val onCloseServices: () -> Unit,
)
