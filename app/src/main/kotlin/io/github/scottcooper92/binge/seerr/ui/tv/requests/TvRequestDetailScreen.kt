package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.tv.component.TvCardRow
import com.binge.designsystem.tv.component.TvDetailAction
import com.binge.designsystem.tv.component.TvDetailActionRow
import com.binge.designsystem.tv.component.TvDetailHero
import com.binge.designsystem.tv.component.TvDetailHeroItem
import com.binge.designsystem.tv.component.TvHeroOverview
import com.binge.designsystem.tv.focus.TvOverlayCloser
import com.binge.designsystem.tv.focus.rememberTvOverlayCloser
import com.binge.designsystem.tv.nav.tvContentGutterStart
import com.binge.designsystem.tv.template.TvDetailPage
import com.binge.designsystem.tv.template.TvDetailPageScope
import com.binge.designsystem.tv.template.TvMessagePage
import com.binge.designsystem.tv.template.TvPageAction
import com.binge.designsystem.tv.template.TvPageHosting
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.requests.ModerationEvent
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetail
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailUiState
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.isError
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes
import io.github.scottcooper92.binge.seerr.ui.requests.messageRes
import io.github.scottcooper92.binge.seerr.ui.requests.statusChip
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheet
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetConfirm
import io.github.scottcooper92.binge.seerr.ui.tv.TvBackdropArtwork
import io.github.scottcooper92.binge.seerr.ui.tv.TvFormNote
import io.github.scottcooper92.binge.seerr.ui.tv.TvFormNoteTone
import io.github.scottcooper92.binge.seerr.ui.tv.TvLoadingPlate
import io.github.scottcooper92.binge.seerr.ui.tv.rememberTvTransientEvent
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.tv.R as TvR

/** Everything the TV request detail page can ask of its host, in one place so the overlay stays a wiring. */
internal class TvRequestDetailActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    /** Null hides the affordance — Binge is not expected to answer, and there is no browser to fall back to. */
    val onOpenInBinge: (() -> Unit)?,
    val onApprove: () -> Unit,
    val onRetryRequest: () -> Unit,
    val onDecline: (Boolean) -> Unit,
    val onRemove: (Boolean) -> Unit,
    /** Blocks the title alone, leaving the request as it is. */
    val onBlock: () -> Unit,
)

/**
 * One request as a read-only television page: the title over its state, who asked and when, the seasons
 * and their status, and what is downloading now. The same [RequestDetailUiState] the phone's
 * `RequestDetailScreen` renders — this is a TV surface over the same ViewModel, so a moderation elsewhere
 * (or this page's own) shows here without any polling. Presented as a full-screen overlay above the rail,
 * so it owns Back itself rather than leaving it to the shell.
 */
@Composable
internal fun TvRequestDetailScreen(
    state: RequestDetailUiState,
    events: Flow<ModerationEvent>,
    actions: TvRequestDetailActions,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = actions.onBack)
    // A removed request has no page to stay on.
    LaunchedEffect(events) {
        events.collect { if (it.removesTheRequest) actions.onBack() }
    }
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state) {
            // The TV page does not seed from a cached row: it loads, or fails, as it always has.
            RequestDetailUiState.Loading, is RequestDetailUiState.Seeded -> {
                val failure = (state as? RequestDetailUiState.Seeded)?.error
                if (failure == null) {
                    TvLoadingPlate(modifier = Modifier.fillMaxSize(), hosting = TvPageHosting.Overlay)
                } else {
                    TvErrorPlate(failure, actions.onRetry)
                }
            }
            is RequestDetailUiState.Error -> TvErrorPlate(state.error, actions.onRetry)
            is RequestDetailUiState.Ready -> TvRequestDetailContent(detail = state.detail, events = events, given = actions)
        }
    }
}

@Composable
private fun TvErrorPlate(
    error: SeerrError,
    onRetry: () -> Unit,
) {
    TvMessagePage(
        body = stringResource(error.messageRes()),
        hosting = TvPageHosting.Overlay,
        icon = Icons.Filled.Warning,
        primary = TvPageAction(stringResource(R.string.hub_retry), onRetry),
    )
}

/**
 * The loaded page, laid out as Binge's TV detail pages are: a hero band with the poster, the title over its state
 * and a synopsis, and the actions at its foot; then a row of seasons, a row of what is downloading, and a row of
 * details, each a [TvCardRow] of focusable cards. [TvDetailPage] owns the scroll: the focused section is pulled to
 * a fixed rest under the hero, so crossing sections never leaves one flush against the bottom edge.
 *
 * Arrival focus lands on the action row; a request this viewer cannot act on and cannot hand to Binge has none, so
 * the synopsis takes focus instead, and the page is never left with nowhere for the D-pad to land.
 */
@Composable
private fun TvRequestDetailContent(
    detail: RequestDetail,
    events: Flow<ModerationEvent>,
    given: TvRequestDetailActions,
) {
    // An approve, a decline, a retry or a block that lands reloads the page without its own button, and the focus that
    // button held would go with it to nowhere the D-pad can reach. Each marks the row to be focused again when the page's
    // actions change (#801).
    var refocusRow by remember { mutableStateOf(false) }
    val actions = remember(given) { given.markingRefocus { refocusRow = true } }
    // The two actions that cannot be undone take a confirm; the rest run at once, since a decline keeps the request.
    var confirming by rememberSaveable { mutableStateOf<DetailConfirm?>(null) }
    val event = rememberTvTransientEvent(events)
    val actionRowFocus = remember { FocusRequester() }
    val removeFocus = remember { FocusRequester() }
    val blockFocus = remember { FocusRequester() }
    val synopsisFocus = remember { FocusRequester() }
    var synopsisFocused by remember { mutableStateOf(false) }
    val removeCloser = rememberTvOverlayCloser(restoreTo = removeFocus, onClose = { confirming = null })
    val blockCloser = rememberTvOverlayCloser(restoreTo = blockFocus, onClose = { confirming = null })
    val item = detail.item
    val allowed = detail.actions
    val actionList =
        requestDetailActions(
            allowed = allowed,
            actions = actions,
            onConfirm = { confirming = it },
            removeFocus = removeFocus,
            blockFocus = blockFocus,
        )
    LaunchedEffect(allowed) {
        if (refocusRow) {
            refocusRow = false
            // An action that emptied the row leaves the synopsis, which the hero offers in its place.
            if (actionList.isNotEmpty()) actionRowFocus.requestFocus() else synopsisFocus.requestFocus()
        }
    }
    // Resolved here: the section builder below is not composable, so it cannot read resources itself.
    val infoCards = requestInfoCards(detail)
    Box(modifier = Modifier.fillMaxSize()) {
        TvDetailPage(
            entryFocus = if (actionList.isEmpty()) synopsisFocus else actionRowFocus,
            hosting = TvPageHosting.Overlay,
        ) {
            hero {
                RequestHero(
                    item = item,
                    actionList = actionList,
                    actionRowFocus = actionRowFocus,
                    synopsisFocus = synopsisFocus,
                    synopsisFocused = synopsisFocused,
                    onSynopsisFocused = { synopsisFocused = it },
                )
            }
            requestSections(detail, infoCards)
        }
        event?.let {
            TvFormNote(
                text = stringResource(it.messageRes()),
                tone = if (it.isError()) TvFormNoteTone.Error else TvFormNoteTone.Success,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = tvContentGutterStart(), bottom = dimensionResource(TvR.dimen.tv_overscan_vertical)),
            )
        }
    }
    confirming?.let { step ->
        TvDetailConfirmSheet(step = step, actions = actions, removeCloser = removeCloser, blockCloser = blockCloser)
    }
}

/** The hero band: poster, the title over its state, and the synopsis when there is no action to take focus first. */
@Composable
private fun RequestHero(
    item: RequestItem,
    actionList: List<TvDetailAction>,
    actionRowFocus: FocusRequester,
    synopsisFocus: FocusRequester,
    synopsisFocused: Boolean,
    onSynopsisFocused: (Boolean) -> Unit,
) {
    val separator = stringResource(R.string.hub_meta_separator)
    val chip = item.statusChip()
    val title = item.title ?: stringResource(item.mediaType.labelRes())
    TvDetailHero(
        item =
            TvDetailHeroItem(
                title = title,
                overline =
                    listOfNotNull(
                        stringResource(item.mediaType.labelRes()),
                        stringResource(R.string.settings_service_4k).takeIf { item.is4k },
                    ).joinToString(separator),
                overview = item.overview,
                facts =
                    listOfNotNull(
                        item.year,
                        stringResource(chip.labelRes),
                        item.requestedBy ?: stringResource(R.string.requests_requester_unknown),
                        formatRelativeOrAbsolute(item.requestedAtMillis),
                    ),
                certification = item.certification,
            ),
        artwork = { TvBackdropArtwork(item.backdropUrl, item.posterUrl) },
        poster = {
            item.posterUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        overview =
            if (actionList.isEmpty()) {
                TvHeroOverview(
                    isFocused = synopsisFocused,
                    onFocusChanged = onSynopsisFocused,
                    onClick = {},
                    focusRequester = synopsisFocus,
                )
            } else {
                null
            },
    ) {
        TvDetailActionRow(actions = actionList, entryFocus = actionRowFocus)
    }
}

/** The rows below the hero: the seasons asked for, what is downloading, and the request's details. */
private fun TvDetailPageScope.requestSections(
    detail: RequestDetail,
    infoCards: List<TvInfoCardItem>,
) {
    if (detail.seasons.isNotEmpty()) {
        section("seasons") { onFocused ->
            TvCardRow(
                items = detail.seasons,
                key = { it.number },
                cellWidth = dimensionResource(R.dimen.tv_detail_season_card_width),
                heading = stringResource(R.string.request_seasons),
                onCellFocused = onFocused,
            ) { season, isFocused, onFocusChanged, cellModifier ->
                TvSeasonCard(season, isFocused, onFocusChanged, cellModifier)
            }
        }
    }
    if (detail.downloads.isNotEmpty()) {
        section("downloads") { onFocused ->
            TvCardRow(
                items = detail.downloads.mapIndexed { index, download -> index to download },
                key = { it.first },
                cellWidth = dimensionResource(R.dimen.tv_detail_download_card_width),
                heading = stringResource(R.string.request_downloads),
                onCellFocused = onFocused,
            ) { (_, download), isFocused, onFocusChanged, cellModifier ->
                TvDownloadCard(download, isFocused, onFocusChanged, cellModifier)
            }
        }
    }
    if (infoCards.isNotEmpty()) {
        section("details") { onFocused ->
            TvCardRow(
                items = infoCards,
                key = { it.label },
                cellWidth = dimensionResource(R.dimen.tv_detail_info_card_width),
                heading = stringResource(R.string.tv_detail_details),
                onCellFocused = onFocused,
            ) { card, isFocused, onFocusChanged, cellModifier ->
                TvInfoCard(card, isFocused, onFocusChanged, cellModifier)
            }
        }
    }
}

/** These actions, with [mark] run first by those whose success removes their own button: Approve, Decline, Retry and Block. */
private fun TvRequestDetailActions.markingRefocus(mark: () -> Unit) =
    TvRequestDetailActions(
        onBack = onBack,
        onRetry = onRetry,
        onOpenInBinge = onOpenInBinge,
        onApprove = {
            mark()
            onApprove()
        },
        onRetryRequest = {
            mark()
            onRetryRequest()
        },
        onDecline = {
            mark()
            onDecline(it)
        },
        onRemove = onRemove,
        onBlock = {
            mark()
            onBlock()
        },
    )

internal enum class DetailConfirm { Remove, Block }

/** The confirm for an action that cannot be taken back: removing the request, or blocking its title. */
@Composable
private fun TvDetailConfirmSheet(
    step: DetailConfirm,
    actions: TvRequestDetailActions,
    removeCloser: TvOverlayCloser,
    blockCloser: TvOverlayCloser,
) {
    val closer = if (step == DetailConfirm.Remove) removeCloser else blockCloser
    TvActionSheet(onDismiss = closer::close) { entryFocus ->
        when (step) {
            DetailConfirm.Remove ->
                TvActionSheetConfirm(
                    title = stringResource(R.string.request_remove_confirm_title),
                    message = stringResource(R.string.request_remove_confirm_message),
                    confirmLabel = stringResource(R.string.request_remove),
                    onConfirm = {
                        actions.onRemove(false)
                        closer.close()
                    },
                    onCancel = closer::close,
                    entryFocus = entryFocus,
                )
            DetailConfirm.Block ->
                TvActionSheetConfirm(
                    title = stringResource(R.string.request_block_confirm_title),
                    message = stringResource(R.string.request_block_confirm_message),
                    confirmLabel = stringResource(R.string.tv_detail_block),
                    onConfirm = {
                        actions.onBlock()
                        closer.close()
                    },
                    onCancel = closer::close,
                    entryFocus = entryFocus,
                )
        }
    }
}
