package com.cooper.binge.companion.seerr.ui.discover.collection

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.StatusChip
import com.binge.designsystem.component.showSnackbar
import com.binge.designsystem.template.CollectionDetailCallbacks
import com.binge.designsystem.template.CollectionDetailScreen
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.collection.CollectionDetails
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.discover.request.RequestFilm
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheet
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetTarget
import com.cooper.binge.companion.seerr.ui.discover.request.filmsSubmitLabel
import com.cooper.binge.companion.seerr.ui.discover.request.messageRes
import com.cooper.binge.companion.seerr.ui.discover.title.statusStyle
import com.cooper.binge.companion.seerr.ui.rememberAllowLocalNetwork
import com.cooper.binge.companion.seerr.ui.state.messageRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

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

/**
 * The collection page on the shared [CollectionDetailScreen]: a hero with its count, the story, then every film as a row that opens the
 * film's page. The pinned Request button is the template's footer, the overflow sits in the bar, and the request sheet and block
 * confirm are its overlays.
 */
@Composable
internal fun CollectionPageScreen(
    state: CollectionPageUiState,
    events: Flow<CollectionPageEvent>,
    actions: CollectionPageActions,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    CollectionEventSnackbars(events, snackbar)
    val ready = state as? CollectionPageUiState.Ready
    val denied = (state as? CollectionPageUiState.Error)?.error == SeerrError.LocalNetworkDenied
    val allow = if (denied) rememberAllowLocalNetwork(actions.onRetry) else null
    val detail = remember(state, resources, allow) { state.toDetailState(resources, allow) }
    val partsByKey =
        remember(ready?.collection) {
            ready
                ?.collection
                ?.parts
                .orEmpty()
                .associateBy { partKey(it.tmdbId) }
        }
    val mayRequest = ready != null && ready.mayRequest && ready.collection.toRequest > 0
    CollectionDetailScreen(
        state = detail,
        callbacks =
            CollectionDetailCallbacks(
                onBack = actions.onBack,
                onRetry = actions.onRetry,
                onOpenPart = { key -> partsByKey[key]?.let(actions.onOpenTitle) },
            ),
        modifier = modifier,
        snackbarHostState = snackbar,
        partTrailing = { part ->
            partsByKey[part.key]?.availability?.statusStyle()?.let { style ->
                { StatusChip(stringResource(style.label), style.sentiment) }
            }
        },
        footer = if (mayRequest) ({ RequestAllFooter(ready.collection.toRequest, actions.onRequest) }) else null,
        topBarActions = { glassAlpha ->
            if (ready != null && ready.mayBlock) {
                CollectionOverflowAction(
                    glassBackgroundAlpha = glassAlpha,
                    blocked = ready.collection.blocked,
                    enabled = !ready.changing,
                    onBlock = actions.onAskBlock,
                    onUnblock = { actions.onSetBlocked(false) },
                )
            }
        },
        overlays = { if (ready != null) CollectionOverlays(ready, actions) },
    )
}

@Composable
private fun CollectionEventSnackbars(
    events: Flow<CollectionPageEvent>,
    snackbar: SnackbarHostState,
) {
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
}

@Composable
private fun CollectionOverlays(
    state: CollectionPageUiState.Ready,
    actions: CollectionPageActions,
) {
    val collection = state.collection
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
