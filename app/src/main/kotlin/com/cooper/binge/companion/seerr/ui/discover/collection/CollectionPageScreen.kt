package com.cooper.binge.companion.seerr.ui.discover.collection

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ExpandableOverview
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.collection.CollectionDetails
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.cooper.binge.companion.seerr.ui.state.MediaHeroDetailPage
import com.cooper.binge.companion.seerr.ui.state.MediaHeroDetailPlaceholder
import com.binge.designsystem.R as DesR

class CollectionPageActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenTitle: (TitleResult) -> Unit,
)

/** The collection page: a hero with its count, the story, then every film as a row that opens the film's page. */
@Composable
internal fun CollectionPageScreen(
    state: CollectionPageUiState,
    actions: CollectionPageActions,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    when (state) {
        CollectionPageUiState.Loading -> MediaHeroDetailPlaceholder { LoadingScreen() }
        is CollectionPageUiState.Error -> MediaHeroDetailPlaceholder { ErrorScreen(state.error, onRetry = actions.onRetry) }
        is CollectionPageUiState.Ready -> ReadyPage(state.collection, actions, modifier, scrollState)
    }
}

@Composable
private fun ReadyPage(
    collection: CollectionDetails,
    actions: CollectionPageActions,
    modifier: Modifier,
    scrollState: ScrollState,
) {
    MediaHeroDetailPage(
        title = collection.name,
        backdropUrl = collection.backdropUrl,
        metaText = collection.metaText(),
        onBack = actions.onBack,
        modifier = modifier,
        scrollState = scrollState,
    ) {
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        collection.overview?.let { overview ->
            Column(Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m))) {
                Text(stringResource(R.string.title_page_overview), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(dimensionResource(DesR.dimen.detail_meta_spacing)))
                ExpandableOverview(text = overview)
            }
            Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        }
        Column(
            modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            collection.parts.forEach { part -> CollectionPartRow(part, onClick = { actions.onOpenTitle(part) }) }
        }
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_l)))
    }
}

@Composable
private fun CollectionDetails.metaText(): String {
    val films = pluralStringResource(R.plurals.collection_page_films, parts.size, parts.size)
    return if (toRequest > 0) stringResource(R.string.collection_page_meta, films, toRequest) else films
}
