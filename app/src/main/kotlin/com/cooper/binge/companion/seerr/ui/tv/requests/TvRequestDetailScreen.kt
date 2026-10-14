package com.cooper.binge.companion.seerr.ui.tv.requests

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.tv.component.TV_DETAILS_SECTION_KEY
import com.binge.designsystem.tv.component.TvCardRow
import com.binge.designsystem.tv.component.TvDetailAction
import com.binge.designsystem.tv.component.TvDetailActionRow
import com.binge.designsystem.tv.component.TvDetailHero
import com.binge.designsystem.tv.component.TvDetailHeroItem
import com.binge.designsystem.tv.component.TvDetailInfoFooterSection
import com.binge.designsystem.tv.component.TvHeroOverview
import com.binge.designsystem.tv.component.TvInfoGroup
import com.binge.designsystem.tv.component.TvSaveStatus
import com.binge.designsystem.tv.focus.rememberTvOverlayCloser
import com.binge.designsystem.tv.template.TvDetailPage
import com.binge.designsystem.tv.template.TvDetailPageScope
import com.binge.designsystem.tv.template.TvPageHosting
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.requests.EditState
import com.cooper.binge.companion.seerr.ui.requests.MediaStatusChoice
import com.cooper.binge.companion.seerr.ui.requests.ModerationEvent
import com.cooper.binge.companion.seerr.ui.requests.RequestDetail
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.requests.RequestEditorControls
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import com.cooper.binge.companion.seerr.ui.requests.isError
import com.cooper.binge.companion.seerr.ui.requests.labelRes
import com.cooper.binge.companion.seerr.ui.requests.statusChip
import com.cooper.binge.companion.seerr.ui.tv.ClearWhenSaved
import com.cooper.binge.companion.seerr.ui.tv.TvActionRun
import com.cooper.binge.companion.seerr.ui.tv.TvBackdropArtwork
import com.cooper.binge.companion.seerr.ui.tv.TvLoadingPlate
import com.cooper.binge.companion.seerr.ui.tv.TvSeerrErrorPage
import com.cooper.binge.companion.seerr.ui.tv.rememberTvActionRun
import kotlinx.coroutines.flow.Flow

/** Everything the TV request detail page can ask of its host, in one place so the overlay stays a wiring. */
internal data class TvRequestDetailActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    /** Null hides the affordance — Binge is not expected to answer, and there is no browser to fall back to. */
    val onOpenInBinge: (() -> Unit)?,
    /** Opens the title in the media server's TV app; null where there is no app to open it, or it is not installed. */
    val onOpenInMediaServer: (() -> Unit)? = null,
    val onApprove: () -> Unit,
    val onRetryRequest: () -> Unit,
    val onDecline: (Boolean) -> Unit,
    val onRemove: (Boolean) -> Unit,
    /** Blocks the title alone, leaving the request as it is. */
    val onBlock: () -> Unit,
    val onSetMediaStatus: (mediaId: Int, status: MediaStatusChoice, is4k: Boolean) -> Unit,
    /** Null hides Edit: a surface with no editor behind it has nothing to open. */
    val edit: TvRequestEditActions? = null,
)

/** Opening the request's editor, and what its sheet may ask of it once open. */
internal class TvRequestEditActions(
    val onStart: () -> Unit,
    val controls: RequestEditorControls,
)

/**
 * One request as a television page: the title over its state, who asked and when, the seasons
 * and their status, and what is downloading now. What it lets a viewer do is a deliberate subset of the phone's;
 * `requestDetailActions` records the split. The same [RequestDetailUiState] the phone's
 * `RequestDetailScreen` renders — this is a TV surface over the same ViewModel, so a moderation elsewhere
 * (or this page's own) shows here without any polling. Presented as a full-screen page above Home,
 * so it owns Back itself rather than leaving it to the shell.
 */
@Composable
internal fun TvRequestDetailScreen(
    state: RequestDetailUiState,
    events: Flow<ModerationEvent>,
    actions: TvRequestDetailActions,
    modifier: Modifier = Modifier,
    /** The sheet open on arrival, for a frame: a static render cannot press the button that opens one. Production passes null. */
    initialSheet: DetailSheet? = null,
    /** Whether Manage is open on arrival, for a frame. Production passes false. */
    initialManageOpen: Boolean = false,
    /** The action status showing on arrival, for a frame, which never collects [events]. Production passes idle. */
    initialStatus: SaveStatus = SaveStatus.Idle,
    /** The section the page opens anchored on, for a frame that shows what sits below the hero. Production passes null. */
    initialFocusedSectionKey: String? = null,
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
                    TvSeerrErrorPage(failure, actions.onRetry)
                }
            }
            is RequestDetailUiState.Error -> TvSeerrErrorPage(state.error, actions.onRetry)
            is RequestDetailUiState.Ready ->
                TvRequestDetailContent(
                    detail = state.detail,
                    edit = state.edit,
                    now = state.now,
                    events = events,
                    given = actions,
                    initialSheet = initialSheet,
                    initialManageOpen = initialManageOpen,
                    initialStatus = initialStatus,
                    initialFocusedSectionKey = initialFocusedSectionKey,
                    isActing = state.isActing,
                )
        }
    }
}

/**
 * The loaded page, laid out as Binge's TV detail pages are: a hero band with the poster, the title over its state
 * and a synopsis, and the actions at its foot; then a row of seasons and a row of what is downloading, each a
 * [TvCardRow] of focusable cards, and the request's facts in the Details footer. [TvDetailPage] owns the scroll: the
 * focused section is pulled to a fixed rest under the hero, so crossing sections never leaves one flush against the
 * bottom edge.
 *
 * Arrival focus lands on the action row; a request this viewer cannot act on and cannot hand to Binge has none, so
 * the synopsis takes focus instead, and the page is never left with nowhere for the D-pad to land.
 */
@Composable
private fun TvRequestDetailContent(
    detail: RequestDetail,
    edit: EditState?,
    now: Long,
    events: Flow<ModerationEvent>,
    given: TvRequestDetailActions,
    initialSheet: DetailSheet?,
    initialManageOpen: Boolean,
    initialStatus: SaveStatus,
    initialFocusedSectionKey: String?,
    isActing: Boolean,
) {
    // An approve, a decline, a retry or a block that lands reloads the page without its own button, and the focus that
    // button held would go with it to nowhere the D-pad can reach. Each marks the row to be focused again when the page's
    // actions change (#801).
    var refocusRow by remember { mutableStateOf(false) }
    // What the last action is doing, and its answer: it stands under the action row, or in the sheet that ran it, until the
    // user acts again, so a failure always has a Try again within reach (#1519). A title that could not be blocked after a
    // decline or a block of its own is retried as a block. TV's Decline never blocks, so the decline case is defensive.
    val run =
        rememberTvActionRun(events, initialStatus) { event ->
            val blockFailed = event == ModerationEvent.DeclinedButBlockFailed || event == ModerationEvent.BlockFailed
            settle(failed = event.isError(), retry = if (blockFailed) given.onBlock else null)
        }
    val actions = remember(given, run, isActing) { given.markingRefocus { refocusRow = true }.running(run, isActing) }
    // The two actions that cannot be undone take a confirm, and Mark as and Report open their own sheets; the rest run at
    // once, since a decline keeps the request.
    var sheet by rememberSaveable { mutableStateOf(initialSheet) }
    var manageOpen by rememberSaveable { mutableStateOf(initialManageOpen) }
    val sheetFocus = remember { DetailSheet.entries.associateWith { FocusRequester() } }
    val manageFocus = remember { FocusRequester() }
    // The Mark as sheet shows its own status; every other action's stands under the row.
    if (sheet != DetailSheet.MarkAs) run.ClearWhenSaved()
    val actionRowFocus = remember { FocusRequester() }
    val synopsisFocus = remember { FocusRequester() }
    var synopsisFocused by remember { mutableStateOf(false) }
    val item = detail.item
    val allowed = detail.actions
    val plan =
        requestDetailActions(
            detail = detail,
            actions = actions,
            onOpenSheet = {
                // The sheet shows the run as its own, so a failure from the action row is not carried into it. Like the
                // sheet's own scope, this keeps an action still saving: it is this page's, and its answer should land.
                if (it == DetailSheet.MarkAs) run.clearUnlessSaving()
                sheet = it
            },
            onOpenManage = { manageOpen = true },
            sheetFocus = sheetFocus,
            manageFocus = manageFocus,
        )
    val actionList = plan.row
    // A sheet hands focus back to what opened it: its own button in the row, or Manage.
    val fromManage = plan.managed.mapNotNull { it.action.sheet }.toSet()
    val closers =
        DetailSheet.entries.associateWith {
            rememberTvOverlayCloser(
                restoreTo = if (it in fromManage) manageFocus else sheetFocus.getValue(it),
                onClose = { sheet = null },
            )
        }
    val manageCloser = rememberTvOverlayCloser(restoreTo = manageFocus, onClose = { manageOpen = false })
    LaunchedEffect(allowed) {
        if (refocusRow) {
            refocusRow = false
            // An action that emptied the row leaves the synopsis, which the hero offers in its place.
            if (actionList.isNotEmpty()) actionRowFocus.requestFocus() else synopsisFocus.requestFocus()
        }
    }
    // Resolved here: the section builder below is not composable, so it cannot read resources itself.
    val details =
        TvRequestDetails(
            stringResource(R.string.tv_detail_details),
            requestInfoGroups(detail, now),
        )
    Box(modifier = Modifier.fillMaxSize()) {
        TvDetailPage(
            entryFocus = if (actionList.isEmpty()) synopsisFocus else actionRowFocus,
            hosting = TvPageHosting.Overlay,
            initialFocusedSectionKey = initialFocusedSectionKey,
        ) {
            hero {
                RequestHero(
                    item = item,
                    now = now,
                    actionList = actionList,
                    actionRowFocus = actionRowFocus,
                    synopsisFocus = synopsisFocus,
                    synopsisFocused = synopsisFocused,
                    onSynopsisFocused = { synopsisFocused = it },
                    run = run.takeUnless { sheet == DetailSheet.MarkAs },
                )
            }
            requestSections(detail, details)
        }
    }
    if (manageOpen && sheet == null) {
        TvRequestManageSheet(managed = plan.managed, onOpenActionSheet = { manageOpen = false }, onDismiss = manageCloser::close)
    }
    sheet?.let { step ->
        TvRequestDetailSheet(
            host = TvDetailSheetHost(step, closers.getValue(step)),
            actions = actions,
            content = TvDetailSheetContent(detail.item, detail.media, edit, detail.webUrl, detail.serverName),
            run = run,
            isActing = isActing,
        )
    }
}

/** The hero band: poster, the title over its state, and the synopsis when there is no action to take focus first. */
@Composable
private fun RequestHero(
    item: RequestItem,
    now: Long,
    actionList: List<TvDetailAction>,
    actionRowFocus: FocusRequester,
    synopsisFocus: FocusRequester,
    synopsisFocused: Boolean,
    onSynopsisFocused: (Boolean) -> Unit,
    run: TvActionRun?,
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
                        formatRelativeOrAbsolute(item.requestedAtMillis, now),
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
        run?.let { TvSaveStatus(status = it.status, onRetry = it::again) }
    }
}

/** The rows below the hero: the seasons asked for and what is downloading, then the request's details. */
private fun TvDetailPageScope.requestSections(
    detail: RequestDetail,
    details: TvRequestDetails,
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
    // The page's facts in Binge's own Details footer.
    section(TV_DETAILS_SECTION_KEY) { _ ->
        TvDetailInfoFooterSection(header = details.header, groups = details.groups)
    }
}

/** The Details section's content, resolved where resources can be read: the section builder is not composable. */
private class TvRequestDetails(
    val header: String,
    val groups: List<TvInfoGroup>,
)
