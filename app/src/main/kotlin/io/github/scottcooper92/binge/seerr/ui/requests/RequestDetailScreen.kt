package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.IconButtonTone
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.theme.BingeTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.MediaHeroDetailPage
import io.github.scottcooper92.binge.seerr.ui.state.MediaHeroDetailScaffold
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

class RequestDetailActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onReportIssue: (IssueType, String) -> Unit,
    val onDismissReport: () -> Unit,
    val onApprove: () -> Unit,
    val onRetryRequest: () -> Unit,
    val onDecline: (Boolean) -> Unit,
    val onRemove: (Boolean) -> Unit,
    val onStartEdit: () -> Unit,
    /**
     * Shows another request's actions, over this page. A slot rather than a callback because its view
     * model belongs to whichever entry owns this page's own, and a screen takes no view model.
     */
    val siblingSheet: @Composable (SiblingSheet) -> Unit,
    /** Opens the requester's, or the moderator's, own user detail screen. */
    val onOpenUser: (Int) -> Unit,
    val edit: EditRequestActions,
    val media: ManageMediaActions,
)

/**
 * Another request against this title, whose actions open in a sheet over this page. [open] is separate
 * from the request being present: dismissing hides the sheet but keeps the request's view model alive
 * until its result lands, so a moderation's snackbar and this page's reload are not dropped with it.
 */
class SiblingSheet(
    val requestId: Int,
    val open: Boolean,
    val snackbarHostState: SnackbarHostState,
    val onDismiss: () -> Unit,
    /** A moderation of that request finished, so this page's list of requests is stale. */
    val onChanged: () -> Unit,
    /** What the opener already knows of the request, so the sheet shows it at once while the detail loads. */
    val preview: RequestPreview? = null,
)

/** A request and what this viewer may do to it, as a list row knows them before its detail has loaded. */
class RequestPreview(
    val item: RequestItem,
    val actions: RequestActions,
)

/**
 * One request as a page: the title over its backdrop, the request's state and history, the
 * seasons, where it went, and what is downloading. This app renders no title page: the title
 * itself opens in the server's web client.
 */
@Composable
fun RequestDetailScreen(
    state: RequestDetailUiState,
    events: Flow<ModerationEvent>,
    actions: RequestDetailActions,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    ModerationSnackbarEffect(events, snackbarHostState)
    // A removed request has no page to stay on.
    LaunchedEffect(events) {
        events.collect { if (it.removesTheRequest) actions.onBack() }
    }
    MediaHeroDetailScaffold(snackbarHostState = snackbarHostState) {
        when (state) {
            RequestDetailUiState.Loading -> RequestDetailSkeleton()
            is RequestDetailUiState.Error ->
                ErrorScreen(error = state.error, modifier = Modifier.safeDrawingPadding(), onRetry = actions.onRetry)
            is RequestDetailUiState.Ready -> Ready(state, actions, snackbarHostState)
        }
    }
}

@Composable
private fun Ready(
    state: RequestDetailUiState.Ready,
    actions: RequestDetailActions,
    snackbarHostState: SnackbarHostState,
) {
    val detail = state.detail
    var reporting by rememberSaveable { mutableStateOf(false) }
    var acting by rememberSaveable { mutableStateOf(false) }
    var opening by rememberSaveable { mutableStateOf(false) }
    // The other request whose actions were last opened, and whether its sheet is up.
    var other by rememberSaveable { mutableStateOf<Int?>(null) }
    var otherOpen by rememberSaveable { mutableStateOf(false) }
    val links = rememberRequestOpenLinks(detail)
    RequestDetailPage(
        detail = detail,
        onBack = actions.onBack,
        onOpen = { opening = true }.takeIf { links.isNotEmpty() },
        onReport = { reporting = true }.takeIf { detail.canReportIssue },
        onOpenRequest = { id ->
            if (id == detail.item.id) {
                acting = true
            } else {
                other = id
                otherOpen = true
            }
        },
        onOpenUser = actions.onOpenUser,
    )
    RequestManagementSheets(state = state, actions = actions, acting = acting, onDismissActing = { acting = false })
    other?.let { id ->
        actions.siblingSheet(
            SiblingSheet(
                requestId = id,
                open = otherOpen,
                snackbarHostState = snackbarHostState,
                onDismiss = { otherOpen = false },
                onChanged = actions.onRetry,
                preview = detail.siblings.firstOrNull { it.id == id }?.let { RequestPreview(detail.item.previewOf(it), RequestActions()) },
            ),
        )
    }
    if (opening) {
        RequestOpenSheet(links = links, onDismiss = { opening = false })
    }
    if (reporting) {
        ReportIssueSheet(
            report = state.report,
            onSend = actions.onReportIssue,
            onDismiss = {
                reporting = false
                actions.onDismissReport()
            },
        )
    }
}

/**
 * What one request's actions can open: the actions sheet while [acting], the editor while it has state,
 * and the media status sheet the actions sheet asks for. Shared by this page's own request and by any
 * other request opened over it, so the two cannot drift.
 */
@Composable
internal fun RequestManagementSheets(
    state: RequestDetailUiState.Ready?,
    actions: RequestDetailActions,
    acting: Boolean,
    onDismissActing: () -> Unit,
    preview: RequestPreview? = null,
    detailLoad: SheetDetailLoad = SheetDetailLoad.Loaded,
    onRetryDetail: () -> Unit = {},
) {
    val detail = state?.detail
    // Which instance the status sheet is marking, keyed by is4k because that is what tells the
    // two apart — and because a Boolean survives process death where MediaInstance would not.
    var marking by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val item = detail?.item ?: preview?.item
    // One sheet from the preview to the loaded detail, so it grows in place rather than closing and reopening.
    if (acting && item != null) {
        RequestActionsSheet(
            item = item,
            actions = detail?.actions ?: preview?.actions ?: RequestActions(),
            onApprove = actions.onApprove,
            onRetry = actions.onRetryRequest,
            onDecline = actions.onDecline,
            onRemove = actions.onRemove,
            onDismiss = onDismissActing,
            canEdit = detail?.canEdit == true,
            onEdit = actions.onStartEdit,
            media = detail?.media,
            mediaActions = actions.media,
            onMarkStatus = { is4k -> marking = is4k },
            onOpenUser = actions.onOpenUser,
            viewerId = detail?.viewerId,
            canManageUsers = detail?.canManageUsers == true,
            detailLoad = detailLoad,
            onRetryDetail = onRetryDetail,
        )
    }
    state?.edit?.let { edit -> EditRequestSheet(item = state.detail.item, edit = edit, actions = actions.edit) }
    val media = detail?.media
    if (media != null) {
        marking?.let { is4k ->
            media.instances.firstOrNull { it.is4k == is4k }?.let { instance ->
                MediaStatusSheet(
                    instance = instance,
                    onSelect = { status -> actions.media.onSetStatus(media.mediaId, status, is4k) },
                    onDismiss = { marking = null },
                )
            }
        }
    }
}

/**
 * The page itself, with no sheet state of its own so a frame can render it. The hero, the overlay bar
 * and the footer's own layout live in [MediaHeroDetailPage] — this composable is what feeds it: the
 * title/backdrop/meta, the [onOpen]/[onReport] icon actions (null when this viewer, or this title,
 * does not have the action) and the scrollable facts as the body; [RequestCardSection] carries the
 * request's own action, so there is no footer.
 */
@Composable
internal fun RequestDetailPage(
    detail: RequestDetail,
    onBack: () -> Unit,
    onOpen: (() -> Unit)?,
    onReport: (() -> Unit)?,
    onOpenRequest: (Int) -> Unit,
    onOpenUser: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    initiallyOverflowing: Boolean = false,
) {
    val item = detail.item
    val title = item.title ?: stringResource(item.mediaType.labelRes())
    MediaHeroDetailPage(
        title = title,
        backdropUrl = detail.backdropUrl,
        metaText = "",
        metaContent = { RequestHeroMeta(detail) },
        onBack = onBack,
        modifier = modifier,
        scrollState = scrollState,
        topBarActions = {
            onOpen?.let {
                ExpressiveIconButton(
                    onClick = it,
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.request_open_elsewhere),
                    tint = BingeTheme.colors.onScrim,
                    tone = IconButtonTone.Glass,
                    size = dimensionResource(DesR.dimen.top_bar_icon_size),
                )
            }
            onReport?.let {
                ExpressiveIconButton(
                    onClick = it,
                    icon = Icons.Filled.ReportProblem,
                    contentDescription = stringResource(R.string.issue_report_title),
                    tint = BingeTheme.colors.onScrim,
                    tone = IconButtonTone.Glass,
                    size = dimensionResource(DesR.dimen.top_bar_icon_size),
                )
            }
        },
        body = {
            RequestHeadline(detail, Modifier.padding(resolvedContentInset()), initiallyOverflowing)
            RequestStats(detail)
            RequestCardSection(detail, onOpenRequest = onOpenRequest, onOpenUser = onOpenUser)
            RequestSections(detail)
        },
    )
}

/** A sibling request's row as a request item, so its sheet opens with its header before its own detail loads. */
private fun RequestItem.previewOf(summary: RequestSummary): RequestItem =
    copy(
        id = summary.id,
        requestedBy = summary.requestedBy,
        requestedById = null,
        requestedAtMillis = summary.requestedAtMillis,
        status = summary.status,
        is4k = summary.is4k,
        seasonNumbers = summary.seasonNumbers,
        download = null,
    )
