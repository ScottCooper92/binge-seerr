package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.ui.openInBrowser
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen

/**
 * How a card opens a title: its kind, its TMDB id and its page on the server, which the flag-off fallback opens. The nav host
 * provides the real one: the title's page here with the discovery flag on, and the hand-off to Binge or the server's page
 * without it. The default does nothing, so a screen composed without a host (a frame) opens nowhere.
 */
val LocalOpenTitle = compositionLocalOf<(TitleKind, Int, String) -> Unit> { { _, _, _ -> } }

/** The page of one title. Only a film has a page so far; any other kind is empty until its own page lands. */
@Composable
internal fun TitleDetailEntry(
    kind: TitleKind,
    tmdbId: Int,
    onBack: () -> Unit,
) {
    if (kind != TitleKind.Movie) {
        EmptyScreen(message = "")
        return
    }
    val viewModel = hiltViewModel<TitlePageViewModel, TitlePageViewModel.Factory>(creationCallback = { it.create(tmdbId) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openTitle = LocalOpenTitle.current
    TitlePageScreen(
        state = state,
        events = viewModel.events,
        actions =
            TitlePageActions(
                onBack = onBack,
                onRetry = viewModel::load,
                onRequest = viewModel::openRequestSheet,
                onCloseRequest = viewModel::closeRequestSheet,
                onRequested = viewModel::requested,
                onOpenTitle = { result: TitleResult -> openTitle(result.kind, result.tmdbId, "") },
                onOpenPerson = {},
                onOpenCollection = {},
                onOpenUrl = context::openInBrowser,
            ),
    )
}
