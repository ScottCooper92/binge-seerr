package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheet
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetTarget
import com.cooper.binge.companion.seerr.ui.discover.request.messageRes
import com.cooper.binge.companion.seerr.ui.requests.SiblingSheet
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.MediaHeroDetailPage
import com.cooper.binge.companion.seerr.ui.state.MediaHeroDetailPlaceholder
import com.cooper.binge.companion.seerr.ui.state.WideHeader
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

/** The series page: a hero, its stats, and the actions, with Request first; then the seasons, facts and people. */
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
        SeriesPageUiState.Loading -> TitlePageSkeleton(onBack = actions.onBack, modifier = modifier)
        is SeriesPageUiState.Error -> MediaHeroDetailPlaceholder { ErrorScreen(state.error, onRetry = actions.onRetry) }
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
    val resources = LocalContext.current.resources
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is SeriesPageEvent.Requested -> snackbar.showSnackbar(resources.getString(event.result.messageRes()))
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
    MediaHeroDetailPage(
        title = series.name,
        backdropUrl = series.backdropUrl,
        metaText = "",
        genres = series.genres,
        tagline = series.tagline,
        externalScores = state.extras?.ratings.toExternalScores(context.resources, LocalConfiguration.current.locales[0]),
        wideHeader = WideHeader(series.posterUrl, series.overview, series.titleStats(LocalConfiguration.current.locales[0])),
        onBack = actions.onBack,
        modifier = modifier,
        snackbarHostState = snackbar,
        scrollState = scrollState,
        topBarActions = { glassAlpha -> ShareBarAction(glassAlpha) { context.shareTitle(series.name, series.webUrl) } },
    ) {
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        SeriesPageBody(state, actions, seasonActions)
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
                    posterUrl = series.posterUrl,
                    meta = series.requestMeta(),
                ),
            onDismiss = actions.onCloseRequest,
            onResult = actions.onRequested,
        )
    }
    if (state.statusSheetOpen) {
        TitleStatusSheet(
            state = titleActionsModel(state.series.standard, state.series.fourK, state.series.requests).primary.state,
            watchUrl = state.series.watchUrl,
            mediaServerName = state.series.mediaServerName,
            onWatch = actions.onOpenUrl,
            onDismiss = actions.onCloseStatus,
        )
    }
    state.manageRequestId?.let { requestId ->
        actions.manageSheet(
            SiblingSheet(
                requestId,
                open = state.manageSheetOpen,
                snackbarHostState = snackbar,
                onDismiss = actions.onCloseManage,
                onChanged = actions.onReload,
            ),
        )
    }
}
