package com.cooper.binge.companion.seerr.ui.discover.collection

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.ExpandableOverview
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.collection.CollectionDetails
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.ui.discover.request.RequestFilm
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheet
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetTarget
import com.cooper.binge.companion.seerr.ui.discover.request.filmsSubmitLabel
import com.cooper.binge.companion.seerr.ui.discover.request.messageRes
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.cooper.binge.companion.seerr.ui.state.MediaHeroDetailPage
import com.cooper.binge.companion.seerr.ui.state.MediaHeroDetailPlaceholder
import com.cooper.binge.companion.seerr.ui.state.messageRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import com.binge.designsystem.R as DesR

class CollectionPageActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenTitle: (TitleResult) -> Unit,
    val onRequest: () -> Unit,
    val onCloseRequest: () -> Unit,
    val onRequested: (RequestResult) -> Unit,
    val onAskBlock: () -> Unit,
    val onDismissBlock: () -> Unit,
    val onSetBlocked: (Boolean) -> Unit,
)

/** The collection page: a hero with its count, the story, then every film as a row that opens the film's page. */
@Composable
internal fun CollectionPageScreen(
    state: CollectionPageUiState,
    events: Flow<CollectionPageEvent>,
    actions: CollectionPageActions,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    when (state) {
        CollectionPageUiState.Loading -> MediaHeroDetailPlaceholder { LoadingScreen() }
        is CollectionPageUiState.Error -> MediaHeroDetailPlaceholder { ErrorScreen(state.error, onRetry = actions.onRetry) }
        is CollectionPageUiState.Ready -> ReadyPage(state, events, actions, modifier, scrollState)
    }
}

@Composable
private fun ReadyPage(
    state: CollectionPageUiState.Ready,
    events: Flow<CollectionPageEvent>,
    actions: CollectionPageActions,
    modifier: Modifier,
    scrollState: ScrollState,
) {
    val collection = state.collection
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collectLatest { event ->
            snackbar.currentSnackbarData?.dismiss()
            val (message, kind) =
                when (event) {
                    is CollectionPageEvent.Requested -> event.result.messageRes() to SnackbarMessageKind.Confirmation
                    is CollectionPageEvent.Changed ->
                        (if (event.blocked) R.string.blocklist_collection_blocked else R.string.blocklist_collection_unblocked) to
                            SnackbarMessageKind.Confirmation
                    is CollectionPageEvent.Failed -> event.error.messageRes() to SnackbarMessageKind.Error
                }
            snackbar.showSnackbar(resources.getString(message), kind)
        }
    }
    val mayRequest = state.mayRequest && collection.toRequest > 0
    MediaHeroDetailPage(
        title = collection.name,
        backdropUrl = collection.backdropUrl,
        metaText = collection.metaText(state.mayRequest),
        onBack = actions.onBack,
        modifier = modifier,
        scrollState = scrollState,
        snackbarHostState = snackbar,
        footer = if (mayRequest) ({ RequestAllFooter(collection.toRequest, actions.onRequest) }) else null,
        topBarActions = { glassAlpha ->
            if (state.mayBlock) {
                CollectionOverflowAction(
                    glassBackgroundAlpha = glassAlpha,
                    blocked = collection.blocked,
                    enabled = !state.changing,
                    onBlock = actions.onAskBlock,
                    onUnblock = { actions.onSetBlocked(false) },
                )
            }
        },
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
    if (state.requestSheetOpen) {
        RequestSheet(target = collection.toRequestTarget(), onDismiss = actions.onCloseRequest, onResult = actions.onRequested)
    }
    if (state.confirmingBlock) {
        CollectionBlockConfirm(collection.name, collection.blockReachesHeldFilms, onConfirm = {
            actions.onSetBlocked(true)
        }, onDismiss = actions.onDismissBlock)
    }
}

/** The pinned Request button: the page's one primary, naming how many films it would ask for. */
@Composable
private fun RequestAllFooter(
    count: Int,
    onRequest: () -> Unit,
) {
    BingeActionFooter(label = filmsSubmitLabel(count), onClick = onRequest)
}

/** The request sheet's target for a collection: its films in release order, standard version only. */
private fun CollectionDetails.toRequestTarget() =
    RequestSheetTarget(
        kind = TitleKind.Movie,
        tmdbId = id,
        title = name,
        standardOpen = parts.any { it.availability == TitleAvailability.None },
        fourKOpen = parts.any { (parts4k[it.tmdbId] ?: TitleAvailability.None) == TitleAvailability.None },
        posterUrl = posterUrl,
        films = parts.map { RequestFilm(it.tmdbId, it.name, it.year, it.availability, parts4k[it.tmdbId] ?: TitleAvailability.None) },
    )

@Composable
private fun CollectionDetails.metaText(mayRequest: Boolean): String {
    val films = pluralStringResource(R.plurals.collection_page_films, parts.size, parts.size)
    return if (mayRequest && toRequest > 0) stringResource(R.string.collection_page_meta, films, toRequest) else films
}
