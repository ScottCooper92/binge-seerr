package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeActionFooter
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheet
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetTarget
import com.cooper.binge.companion.seerr.ui.discover.request.messageRes
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.cooper.binge.companion.seerr.ui.state.MediaHeroDetailPage
import com.cooper.binge.companion.seerr.ui.state.MediaHeroDetailPlaceholder
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

class TitlePageActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onRequest: () -> Unit,
    val onCloseRequest: () -> Unit,
    val onRequested: (com.cooper.binge.companion.seerr.discover.request.RequestResult) -> Unit,
    val onOpenTitle: (TitleResult) -> Unit,
    val onOpenPerson: (Int) -> Unit,
    val onOpenCollection: (Int) -> Unit,
    val onOpenUrl: (String) -> Unit,
)

/** The film page: a hero, the facts and people, and Request pinned below; a request is the primary action. */
@Composable
internal fun TitlePageScreen(
    state: TitlePageUiState,
    events: Flow<TitlePageEvent>,
    actions: TitlePageActions,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    when (state) {
        TitlePageUiState.Loading -> MediaHeroDetailPlaceholder { LoadingScreen() }
        is TitlePageUiState.Error -> MediaHeroDetailPlaceholder { ErrorScreen(state.error, onRetry = actions.onRetry) }
        is TitlePageUiState.Ready -> ReadyPage(state, events, actions, modifier, scrollState)
    }
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
    val resources = LocalContext.current.resources
    LaunchedEffect(events) {
        events.collect { event ->
            if (event is TitlePageEvent.Requested) snackbar.showSnackbar(resources.getString(event.result.messageRes()))
        }
    }
    val movie = state.movie
    MediaHeroDetailPage(
        title = movie.title,
        backdropUrl = movie.backdropUrl ?: movie.posterUrl,
        metaText = movie.metaLine(),
        onBack = actions.onBack,
        modifier = modifier,
        snackbarHostState = snackbar,
        scrollState = scrollState,
        footer = if (state.mayRequest) ({ BingeActionFooter(stringResource(R.string.title_page_request), actions.onRequest) }) else null,
    ) {
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        TitlePageBody(state, actions)
    }
    if (state.requestSheetOpen) {
        RequestSheet(
            target =
                RequestSheetTarget(
                    kind = com.cooper.binge.companion.seerr.discover.TitleKind.Movie,
                    tmdbId = movie.tmdbId,
                    title = movie.title,
                    standardOpen = movie.standard.mayRequest,
                    fourKOpen = movie.fourK?.mayRequest == true,
                ),
            onDismiss = actions.onCloseRequest,
            onResult = actions.onRequested,
        )
    }
}
