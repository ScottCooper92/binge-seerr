package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.title.TitleCompany
import com.cooper.binge.companion.seerr.ui.openInBrowser
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
    onOpenSeason: (seasonNumber: Int, seasonName: String) -> Unit,
    onOpenCompany: (TitleCompany) -> Unit,
) {
    when (kind) {
        TitleKind.Movie -> FilmPageEntry(tmdbId, onBack, onOpenCredits, onOpenCompany)
        TitleKind.Series -> SeriesPageEntry(tmdbId, onBack, onOpenCredits, onOpenSeason, onOpenCompany)
        else -> EmptyScreen(message = "")
    }
}

@Composable
private fun FilmPageEntry(
    tmdbId: Int,
    onBack: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenCompany: (TitleCompany) -> Unit,
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
                onRetry = viewModel::load,
                onRequest = viewModel::openRequestSheet,
                onCloseRequest = viewModel::closeRequestSheet,
                onRequested = viewModel::requested,
                onReload = viewModel::reload,
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
                onRetry = viewModel::load,
                onRequest = viewModel::openRequestSheet,
                onCloseRequest = viewModel::closeRequestSheet,
                onRequested = viewModel::requested,
                onReload = viewModel::reload,
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
    onRetry: () -> Unit,
    onRequest: () -> Unit,
    onCloseRequest: () -> Unit,
    onRequested: (RequestResult) -> Unit,
    onReload: () -> Unit,
): TitlePageActions {
    val context = LocalContext.current
    val openTitle = LocalOpenTitle.current
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
        onReload = onReload,
    )
}
