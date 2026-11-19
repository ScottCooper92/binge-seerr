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
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.title.TitleCompany
import com.cooper.binge.companion.seerr.discover.title.TitleKeyword
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
    /** The sheet of one request, backed by that request's own view model, which the nav entry supplies. */
    val manageSheet: @Composable (SiblingSheet) -> Unit = {},
    /** Report, drawn once the title is in the library; null where the page has nothing to report on. */
    val onReport: (() -> Unit)? = null,
    /** The report sheet, closed. */
    val onCloseReport: () -> Unit = {},
    /** The report form for this title, hosted over the page. */
    val reportSheet: @Composable (onClose: () -> Unit) -> Unit = {},
)

/** The film page: a hero, its stats, and the actions, with Request first; then the facts and people. */
@Composable
internal fun TitlePageScreen(
    state: TitlePageUiState,
    events: Flow<TitlePageEvent>,
    actions: TitlePageActions,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    when (state) {
        TitlePageUiState.Loading -> TitlePageSkeleton(onBack = actions.onBack, modifier = modifier)
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
            when (event) {
                is TitlePageEvent.Requested -> snackbar.showSnackbar(resources.getString(event.result.messageRes()))
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
    val context = LocalContext.current
    MediaHeroDetailPage(
        title = movie.title,
        backdropUrl = movie.backdropUrl,
        metaText = "",
        genres = movie.genres,
        tagline = movie.tagline,
        externalScores = state.extras?.ratings.toExternalScores(context.resources, LocalConfiguration.current.locales[0]),
        wideHeader = WideHeader(movie.posterUrl, movie.overview, movie.titleStats(LocalConfiguration.current.locales[0])),
        onBack = actions.onBack,
        modifier = modifier,
        snackbarHostState = snackbar,
        scrollState = scrollState,
        topBarActions = { glassAlpha -> ShareBarAction(glassAlpha) { context.shareTitle(movie.title, movie.webUrl) } },
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
            state = titleActionsModel(state.movie.standard, state.movie.fourK, state.movie.requests, state.movie.mayReport).primary.state,
            watchUrl = state.movie.watchUrl,
            mediaServerName = state.movie.mediaServerName,
            canRequest = state.movie.canRequest,
            onWatch = actions.onOpenUrl,
            onDismiss = actions.onCloseStatus,
        )
    }
    if (state.reportSheetOpen) actions.reportSheet(actions.onCloseReport)
    state.manageRequestId?.let { requestId ->
        TitleManageSheet(requestId, state.manageSheetOpen, snackbar, movie.sheetFacts(), actions)
    }
}
