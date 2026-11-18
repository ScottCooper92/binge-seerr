package com.cooper.binge.companion.seerr.ui.discover.title.season

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.binge.designsystem.R as DesR

/** Every episode of one season under the season's name. An episode does not open anything. */
@Composable
internal fun SeasonEpisodesScreen(
    title: String,
    emptyMessage: String,
    state: SeasonEpisodesUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BingeScreenScaffold(title = title, modifier = modifier, bar = ScreenBar.Small, onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding.screenOuterPadding())) {
            val inner = padding.screenInnerPadding()
            when (state) {
                SeasonEpisodesUiState.Loading -> LoadingScreen(Modifier.padding(inner))
                is SeasonEpisodesUiState.Error -> ErrorScreen(state.error, Modifier.padding(inner), onRetry)
                is SeasonEpisodesUiState.Ready ->
                    if (state.episodes.isEmpty()) EmptyScreen(emptyMessage, Modifier.padding(inner)) else EpisodeList(state.episodes, inner)
            }
        }
    }
}

@Composable
private fun EpisodeList(
    episodes: List<SeasonEpisode>,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        items(episodes, key = { it.number }) { EpisodeRow(it, Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m))) }
    }
}
