package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.title.TitleCompany
import com.cooper.binge.companion.seerr.discover.title.TitleKeyword
import com.cooper.binge.companion.seerr.flags.LocalFlags
import com.cooper.binge.companion.seerr.flags.bingePromotion
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_MOVIE
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_TV
import com.cooper.binge.companion.seerr.ui.SiblingRequestSheet
import com.cooper.binge.companion.seerr.ui.bingeTitleHandOff
import com.cooper.binge.companion.seerr.ui.openInBrowser
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen

/**
 * How a card opens a title: its kind, its TMDB id and its page on the server, which the flag-off fallback opens. The nav host
 * provides the real one: the title's page here with the discovery flag on, and the hand-off to Binge or the server's page
 * without it. The default does nothing, so a screen composed without a host (a frame) opens nowhere.
 */
val LocalOpenTitle = compositionLocalOf<(TitleKind, Int, String) -> Unit> { { _, _, _ -> } }

/**
 * The page of one title: a film's or a series'. A person and a collection open their own routes, so they never reach here
 * and fall to the empty branch.
 */
@Composable
internal fun TitleDetailEntry(
    kind: TitleKind,
    tmdbId: Int,
    onBack: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenSeason: (seasonNumber: Int, seriesName: String) -> Unit,
    onOpenCompany: (TitleCompany) -> Unit,
    onOpenKeyword: (TitleKeyword) -> Unit,
    onOpenUser: (Int) -> Unit,
    onOpenServices: () -> Unit,
) {
    when (kind) {
        TitleKind.Movie -> FilmPageEntry(tmdbId, onBack, onOpenCredits, onOpenCompany, onOpenKeyword, onOpenUser, onOpenServices)
        TitleKind.Series ->
            SeriesPageEntry(tmdbId, onBack, onOpenCredits, onOpenSeason, onOpenCompany, onOpenKeyword, onOpenUser, onOpenServices)
        else -> EmptyScreen(message = "")
    }
}

@Composable
private fun FilmPageEntry(
    tmdbId: Int,
    onBack: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenCompany: (TitleCompany) -> Unit,
    onOpenKeyword: (TitleKeyword) -> Unit,
    onOpenUser: (Int) -> Unit,
    onOpenServices: () -> Unit,
) {
    val viewModel = hiltViewModel<TitlePageViewModel, TitlePageViewModel.Factory>(creationCallback = { it.create(tmdbId) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
                onCloseRequest = viewModel::closeRequestSheet,
                onRequested = viewModel::requested,
                onReload = viewModel::reload,
                manage =
                    TitleManage(
                        { viewModel.closeSheet(TitleSheet.Manage) },
                        { viewModel.closeSheet(TitleSheet.Status) },
                        viewModel::requestFourKFromManage,
                        viewModel::unblock,
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
                onCloseRequest = viewModel::closeRequestSheet,
                onRequested = viewModel::requested,
                onReload = viewModel::reload,
                manage =
                    TitleManage(
                        { viewModel.closeSheet(TitleSheet.Manage) },
                        { viewModel.closeSheet(TitleSheet.Status) },
                        viewModel::requestFourKFromManage,
                        viewModel::unblock,
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
    onCloseRequest: () -> Unit,
    onRequested: (RequestResult) -> Unit,
    onReload: () -> Unit,
    manage: TitleManage,
    side: TitleSideActions,
): TitlePageActions {
    val context = LocalContext.current
    val openTitle = LocalOpenTitle.current
    val promoted = LocalFlags.current.bingePromotion
    val watchInBinge =
        remember(context, promoted, side.mediaType, side.tmdbId) {
            val mediaType = if (side.mediaType == SEERR_MEDIA_TYPE_TV) RequestMediaType.Tv else RequestMediaType.Movie
            context.bingeTitleHandOff(promoted, mediaType, side.tmdbId)
        }
    return TitlePageActions(
        onBack = onBack,
        onRetry = onRetry,
        onRequest = onRequest,
        onCloseRequest = onCloseRequest,
        onRequested = onRequested,
        onOpenTitle = { result: TitleResult -> openTitle(result.kind, result.tmdbId, "") },
        onOpenPerson = { openTitle(TitleKind.Person, it, "") },
        onOpenCollection = { id -> openTitle(TitleKind.Collection, id, "") },
        onOpenUrl = context::openInBrowser,
        onOpenCredits = onOpenCredits,
        onOpenCompany = onOpenCompany,
        onOpenKeyword = onOpenKeyword,
        onReload = onReload,
        onCloseManage = manage.onClose,
        onCloseStatus = manage.onCloseStatus,
        onRequestFourK = manage.onRequestFourK,
        onUnblock = manage.onUnblock,
        manageSheet = { sheet -> SiblingRequestSheet(sheet, manage.onOpenUser, manage.onOpenServices) },
        onReport = side.onOpen,
        onCloseReport = side.onClose,
        onWatchInBinge = watchInBinge,
        onToggleWatchlist = side.onToggleWatchlist,
        reportSheet = { onClose -> TitleReportSheet(side.mediaType, side.tmdbId, onClose) },
    )
}

/** What the page needs for the sheets over it: how the request's and the status sheet close, and where a requester and the services open. */
private class TitleManage(
    val onClose: () -> Unit,
    val onCloseStatus: () -> Unit,
    val onRequestFourK: () -> Unit,
    val onUnblock: () -> Unit,
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
