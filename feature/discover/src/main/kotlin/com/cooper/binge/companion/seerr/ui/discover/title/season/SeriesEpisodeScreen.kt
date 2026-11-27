package com.cooper.binge.companion.seerr.ui.discover.title.season

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.EpisodeBrowseRow
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.request.SeasonChoices
import com.cooper.binge.companion.seerr.feature.discover.R
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheet
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetTarget
import com.cooper.binge.companion.seerr.ui.discover.request.seasonLabel
import com.cooper.binge.companion.seerr.ui.discover.title.requestMeta
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.binge.designsystem.R as DesR

/**
 * One episode under its show's name: its still, number, name, air date and overview, then the season it is in. The request action asks
 * for that season only, through the same sheet the show's page uses, which is limited here to this one season.
 */
@Composable
fun SeriesEpisodeScreen(
    title: String,
    state: SeriesEpisodeUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRequest: (fourK: Boolean) -> Unit,
    onRequestSheetClose: () -> Unit,
    onRequested: (RequestResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    BingeScreenScaffold(
        title = title,
        modifier = modifier,
        onBack = onBack,
        bar = ScreenBar.Small,
        snackbarHostState = snackbar,
    ) { padding ->
        ScreenBody(padding) { inner ->
            when (state) {
                SeriesEpisodeUiState.Loading -> LoadingScreen(Modifier.padding(inner))
                is SeriesEpisodeUiState.Error -> ErrorScreen(state.error, Modifier.padding(inner), onRetry)
                is SeriesEpisodeUiState.Ready -> {
                    EpisodeContent(state, onRequest, Modifier.padding(inner))
                    if (state.requestSheetOpen) {
                        RequestSheet(
                            target = state.requestTarget(),
                            onDismiss = onRequestSheetClose,
                            onResult = onRequested,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeContent(
    state: SeriesEpisodeUiState.Ready,
    onRequest: (fourK: Boolean) -> Unit,
    modifier: Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val season = seasonName(state)
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        EpisodeBrowseRow(episode = state.episode.toBrowseUi(locale))
        Text(
            text = season,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.screen_content_inset)),
        )
        if (state.canRequest) {
            BingeFilledButton(
                label = stringResource(R.string.episode_request_season, season),
                // Only 4K still has this season open: the sheet opens on that version rather than the standard one.
                onClick = { onRequest(state.fourKOpen && !state.standardOpen) },
                modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.screen_content_inset)),
            )
        }
    }
}

/** This episode's season, named as the sheet and the season list name it. */
@Composable
private fun seasonName(state: SeriesEpisodeUiState.Ready): String =
    seasonLabel(
        state.seasonNumber,
        state.series.seasons
            .firstOrNull { it.number == state.seasonNumber }
            ?.name
            .orEmpty(),
    )

/** The sheet for this one season: only its choice in each version is listed, so the request can ask for nothing else. */
@Composable
private fun SeriesEpisodeUiState.Ready.requestTarget(): RequestSheetTarget =
    RequestSheetTarget(
        kind = TitleKind.Series,
        tmdbId = series.tmdbId,
        title = series.name,
        standardOpen = standardOpen && !requestSheetFourK,
        fourKOpen = fourKOpen,
        seasonChoices = SeasonChoices(standard = listOfNotNull(standard), fourK = listOfNotNull(fourK)),
        partialRequests = series.partialRequests,
        posterUrl = series.posterUrl,
        meta = series.requestMeta(),
    )
