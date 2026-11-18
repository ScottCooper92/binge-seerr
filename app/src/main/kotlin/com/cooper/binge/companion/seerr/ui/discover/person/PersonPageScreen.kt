package com.cooper.binge.companion.seerr.ui.discover.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilterChipRow
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.component.MediaCard
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.person.PersonCredit
import com.cooper.binge.companion.seerr.discover.person.PersonCredits
import com.cooper.binge.companion.seerr.ui.discover.title.cardStatus
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.binge.designsystem.R as DesR

/** What the person page does: read again, narrow the credits, and open a title. */
class PersonPageActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onRetryCredits: () -> Unit,
    val onFilter: (CreditsFilter) -> Unit,
    val onOpenTitle: (kind: TitleKind, tmdbId: Int) -> Unit,
)

/** A person: their profile, then the titles they are in as cast and as crew, one chip row narrowing both. */
@Composable
internal fun PersonPageScreen(
    state: PersonPageUiState,
    actions: PersonPageActions,
    modifier: Modifier = Modifier,
) {
    val title = (state as? PersonPageUiState.Ready)?.person?.name.orEmpty()
    BingeScreenScaffold(title = title, modifier = modifier, bar = ScreenBar.Small, onBack = actions.onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding.screenOuterPadding())) {
            val inner = padding.screenInnerPadding()
            when (state) {
                PersonPageUiState.Loading -> LoadingScreen(Modifier.padding(inner))
                is PersonPageUiState.Error -> ErrorScreen(state.error, Modifier.padding(inner), actions.onRetry)
                is PersonPageUiState.Ready -> PersonGrid(state, actions, inner)
            }
        }
    }
}

@Composable
private fun PersonGrid(
    state: PersonPageUiState.Ready,
    actions: PersonPageActions,
    contentPadding: PaddingValues,
) {
    val gap = dimensionResource(DesR.dimen.padding_m)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(dimensionResource(DesR.dimen.card_width)),
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = gap,
                end = gap,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        full("profile") { PersonProfile(state.person) }
        when (val credits = state.credits) {
            PersonCreditsState.Loading -> full("loading") { LoadingScreen() }
            is PersonCreditsState.Error -> full("error") { ErrorScreen(credits.error, onRetry = actions.onRetryCredits) }
            is PersonCreditsState.Ready -> creditSections(state, credits.credits, actions)
        }
    }
}

private fun LazyGridScope.creditSections(
    state: PersonPageUiState.Ready,
    all: PersonCredits,
    actions: PersonPageActions,
) {
    if (all.isEmpty) return
    full("filters") { FilterChips(state.filter, actions.onFilter) }
    val visible = all.only(state.filter.kind)
    if (visible.isEmpty) {
        full("none") {
            Text(stringResource(R.string.person_credits_filtered_empty), style = MaterialTheme.typography.bodyMedium)
        }
    }
    creditSection("cast", R.string.title_page_cast, visible.cast, actions.onOpenTitle)
    creditSection("crew", R.string.title_page_crew, visible.crew, actions.onOpenTitle)
}

private fun LazyGridScope.creditSection(
    tag: String,
    title: Int,
    credits: List<PersonCredit>,
    onOpen: (TitleKind, Int) -> Unit,
) {
    if (credits.isEmpty()) return
    full("$tag-header") { SectionHeader(stringResource(title)) }
    items(credits, key = { "$tag-${it.key}" }) { credit ->
        MediaCard(
            posterUrl = credit.posterUrl,
            title = credit.title,
            rating = null,
            onClick = { onOpen(credit.kind, credit.tmdbId) },
            status = credit.availability.cardStatus(),
            subtitle = credit.role,
        )
    }
}

@Composable
private fun FilterChips(
    selected: CreditsFilter,
    onSelect: (CreditsFilter) -> Unit,
) {
    val items =
        listOf(
            FilterChipItem(stringResource(R.string.person_filter_all), icon = Icons.Filled.Apps),
            FilterChipItem(stringResource(R.string.person_filter_movies), icon = Icons.Filled.Movie),
            FilterChipItem(stringResource(R.string.person_filter_series), icon = Icons.Filled.Tv),
        )
    BingeFilterChipRow(items, selectedIndex = selected.ordinal, onSelect = { onSelect(CreditsFilter.entries[it]) })
}

private fun LazyGridScope.full(
    key: String,
    content: @Composable () -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
