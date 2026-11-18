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
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheet
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetTarget
import com.cooper.binge.companion.seerr.ui.discover.request.messageRes
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.cooper.binge.companion.seerr.ui.state.MediaHeroDetailPage
import com.cooper.binge.companion.seerr.ui.state.MediaHeroDetailPlaceholder
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

/** The series page: a hero, the seasons, facts and people, and Request pinned below; a request is the primary action. */
@Composable
internal fun SeriesPageScreen(
    state: SeriesPageUiState,
    events: Flow<SeriesPageEvent>,
    actions: TitlePageActions,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    when (state) {
        SeriesPageUiState.Loading -> MediaHeroDetailPlaceholder { LoadingScreen() }
        is SeriesPageUiState.Error -> MediaHeroDetailPlaceholder { ErrorScreen(state.error, onRetry = actions.onRetry) }
        is SeriesPageUiState.Ready -> ReadySeriesPage(state, events, actions, modifier, scrollState)
    }
}

@Composable
private fun ReadySeriesPage(
    state: SeriesPageUiState.Ready,
    events: Flow<SeriesPageEvent>,
    actions: TitlePageActions,
    modifier: Modifier,
    scrollState: ScrollState,
) {
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(events) {
        events.collect { event ->
            if (event is SeriesPageEvent.Requested) snackbar.showSnackbar(resources.getString(event.result.messageRes()))
        }
    }
    val series = state.series
    MediaHeroDetailPage(
        title = series.name,
        backdropUrl = series.backdropUrl ?: series.posterUrl,
        metaText = series.metaLine(),
        onBack = actions.onBack,
        modifier = modifier,
        snackbarHostState = snackbar,
        scrollState = scrollState,
        footer = if (state.mayRequest) ({ BingeActionFooter(stringResource(R.string.title_page_request), actions.onRequest) }) else null,
    ) {
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        SeriesPageBody(state, actions)
    }
    if (state.requestSheetOpen) {
        RequestSheet(
            target =
                RequestSheetTarget(
                    kind = TitleKind.Series,
                    tmdbId = series.tmdbId,
                    title = series.name,
                    standardOpen = series.standard.mayRequest,
                    fourKOpen = series.fourK?.mayRequest == true,
                    seasonChoices = series.seasonChoices,
                    partialRequests = series.partialRequests,
                ),
            onDismiss = actions.onCloseRequest,
            onResult = actions.onRequested,
        )
    }
}
