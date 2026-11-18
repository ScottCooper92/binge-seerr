package com.cooper.binge.companion.seerr.ui.discover.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.component.ExpandableOverview
import com.binge.designsystem.component.MediaCard
import com.binge.designsystem.component.MediaCarousel
import com.binge.designsystem.template.HeroDetailScreen
import com.binge.designsystem.template.HeroPullIndicator
import com.binge.designsystem.template.LocalHeroReadingMargin
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.person.PersonCredit
import com.cooper.binge.companion.seerr.ui.discover.title.ShareBarAction
import com.cooper.binge.companion.seerr.ui.discover.title.cardStatus
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.binge.designsystem.R as DesR

/** What the person page does: read again, share, open a title and open a link. */
class PersonPageActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onRetryCredits: () -> Unit,
    val onRefresh: () -> Unit,
    val onShare: (name: String, url: String) -> Unit,
    val onOpenUrl: (url: String) -> Unit,
    val onOpenTitle: (kind: TitleKind, tmdbId: Int) -> Unit,
)

/**
 * A person under a floating bar, with no image: the portrait and facts, the biography, and the titles they are in as cast and
 * as crew. Loading and a failed load keep the bar, so Back is always there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonPageScreen(
    state: PersonPageUiState,
    actions: PersonPageActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    pullState: PullToRefreshState? = null,
) {
    val ready = state as? PersonPageUiState.Ready
    HeroDetailScreen(
        title = ready?.person?.name.orEmpty(),
        onBack = actions.onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        contentMaxWidth = Dp.Infinity,
        onRefresh = actions.onRefresh.takeIf { ready != null },
        isRefreshing = ready?.refreshing == true,
        pullIndicator = HeroPullIndicator.InBar,
        pullState = pullState,
        actions = { glassAlpha ->
            if (ready != null) {
                ShareBarAction(glassAlpha) { actions.onShare(ready.person.name, ready.person.webUrl) }
            }
        },
        hero = {
            Spacer(
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing).height(dimensionResource(DesR.dimen.person_profile_top_bar_offset)),
            )
        },
    ) {
        when (state) {
            PersonPageUiState.Loading -> PersonSkeleton()
            is PersonPageUiState.Error ->
                ErrorScreen(
                    state.error,
                    Modifier.padding(dimensionResource(DesR.dimen.padding_m)),
                    actions.onRetry,
                )
            is PersonPageUiState.Ready -> PersonBody(state, actions)
        }
    }
}

@Composable
private fun ColumnScope.PersonBody(
    state: PersonPageUiState.Ready,
    actions: PersonPageActions,
) {
    val person = state.person
    val margin = LocalHeroReadingMargin.current + dimensionResource(DesR.dimen.padding_m)
    val gap = dimensionResource(DesR.dimen.padding_m)
    Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.padding(bottom = gap)) {
        PersonProfile(person, Modifier.padding(horizontal = margin))
        person.biography?.let { biography ->
            Column(
                Modifier.padding(horizontal = margin),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            ) {
                Text(stringResource(R.string.person_page_biography), style = MaterialTheme.typography.titleMedium)
                ExpandableOverview(text = biography)
            }
        }
        CreditsSection(state.credits, actions)
        person.links().takeIf { it.isNotEmpty() }?.let { links ->
            Column(
                Modifier.padding(horizontal = margin),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            ) {
                Text(stringResource(R.string.person_page_links), style = MaterialTheme.typography.titleMedium)
                PersonLinksRow(links, actions.onOpenUrl)
            }
        }
    }
}

@Composable
private fun CreditsSection(
    state: PersonCreditsState,
    actions: PersonPageActions,
) {
    when (state) {
        PersonCreditsState.Loading -> CreditsSkeleton()
        is PersonCreditsState.Error ->
            ErrorScreen(state.error, Modifier.padding(dimensionResource(DesR.dimen.padding_m)), actions.onRetryCredits)
        is PersonCreditsState.Ready -> {
            CreditRail(stringResource(R.string.person_page_known_for), state.credits.cast, actions.onOpenTitle)
            CreditRail(stringResource(R.string.title_page_crew), state.credits.crew, actions.onOpenTitle)
        }
    }
}

@Composable
private fun CreditRail(
    title: String,
    credits: List<PersonCredit>,
    onOpen: (TitleKind, Int) -> Unit,
) {
    if (credits.isEmpty()) return
    MediaCarousel(title = title, items = credits, itemKey = { it.key }, onEndTileClick = null) { _, credit ->
        MediaCard(
            posterUrl = credit.posterUrl,
            title = credit.title,
            rating = credit.rating,
            onClick = { onOpen(credit.kind, credit.tmdbId) },
            modifier = Modifier.width(dimensionResource(DesR.dimen.card_width)),
            status = credit.availability.cardStatus(),
            subtitle = credit.role,
        )
    }
}
