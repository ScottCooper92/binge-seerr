package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.IconButtonTone
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.theme.BingeShapes
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
    /** Opens a sibling request's own page — another request against this same title. */
    val onOpenSibling: (Int) -> Unit,
    /** Opens the requester's, or the moderator's, own user detail screen. */
    val onOpenUser: (Int) -> Unit,
    val edit: EditRequestActions,
    val media: ManageMediaActions,
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
            is RequestDetailUiState.Ready -> Ready(state, actions)
        }
    }
}

@Composable
private fun Ready(
    state: RequestDetailUiState.Ready,
    actions: RequestDetailActions,
) {
    val detail = state.detail
    var reporting by rememberSaveable { mutableStateOf(false) }
    var acting by rememberSaveable { mutableStateOf(false) }
    var opening by rememberSaveable { mutableStateOf(false) }
    // Which instance the status sheet is marking, keyed by is4k because that is what tells the
    // two apart — and because a Boolean survives process death where MediaInstance would not.
    var marking by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val links = rememberRequestOpenLinks(detail)
    RequestDetailPage(
        detail = detail,
        onBack = actions.onBack,
        onOpen = { opening = true }.takeIf { links.isNotEmpty() },
        onReport = { reporting = true }.takeIf { detail.canReportIssue },
        onPrimary = { acting = true },
        onOpenSibling = actions.onOpenSibling,
        onOpenUser = actions.onOpenUser,
    )
    if (acting) {
        RequestActionsSheet(
            item = detail.item,
            actions = detail.actions,
            onApprove = actions.onApprove,
            onRetry = actions.onRetryRequest,
            onDecline = actions.onDecline,
            onRemove = actions.onRemove,
            onDismiss = { acting = false },
            canEdit = detail.canEdit,
            onEdit = actions.onStartEdit,
            media = detail.media,
            mediaActions = actions.media,
            onMarkStatus = { is4k -> marking = is4k },
        )
    }
    if (opening) {
        RequestOpenSheet(links = links, onDismiss = { opening = false })
    }
    state.edit?.let { edit -> EditRequestSheet(item = detail.item, edit = edit, actions = actions.edit) }
    val media = detail.media
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
 * The page itself, with no sheet state of its own so a frame can render it. The hero, the overlay bar
 * and the footer's own layout live in [MediaHeroDetailPage] — this composable is what feeds it: the
 * title/backdrop/meta, the [onOpen]/[onReport] icon actions (null when this viewer, or this title,
 * does not have the action), [RequestPrimaryAction] as the footer where [RequestDetail.hasPrimaryAction]
 * is true, and the scrollable facts as the body.
 */
@Composable
internal fun RequestDetailPage(
    detail: RequestDetail,
    onBack: () -> Unit,
    onOpen: (() -> Unit)?,
    onReport: (() -> Unit)?,
    onPrimary: () -> Unit,
    onOpenSibling: (Int) -> Unit,
    onOpenUser: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    initiallyOverflowing: Boolean = false,
) {
    val item = detail.item
    val title = item.title ?: stringResource(item.mediaType.labelRes())
    val footer: (@Composable () -> Unit)? =
        if (detail.hasPrimaryAction) {
            { RequestPrimaryAction(detail = detail, onClick = onPrimary) }
        } else {
            null
        }
    MediaHeroDetailPage(
        title = title,
        backdropUrl = detail.backdropUrl,
        // The type moved onto RequestHeadline's chip row (#340), and the year has now followed it
        // there too, onto the same line as the chips — nothing is left to read in the hero itself.
        metaText = "",
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
        footer = footer,
        body = {
            RequestHeadline(detail, Modifier.padding(resolvedContentInset()), initiallyOverflowing)
            RequestStats(detail)
            RequestFacts(detail, onOpenUser)
            RequestSections(detail)
            RequestSiblings(detail, onOpenSibling)
            // The footer sits below the scroll rather than over it (MediaHeroDetailPage's own KDoc),
            // so this isn't clearing an overlap - it's the same breathing room the scroll's last row
            // would otherwise only get from the footer's own top padding, which reads as cramped.
            if (footer != null) Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_l)))
        },
    )
}

/**
 * One action, anchored below the page's own scrolling content. Its label follows the request's
 * state rather than being a generic "Manage": approving a pending request is the hottest path
 * here, and a label that does not say so buries it behind a tap on exactly what an admin opened
 * the app to do.
 *
 * Rounded and raised, the same [BingeShapes.HeroTop] container a Discover Sliders "Add" or a
 * Permissions page's "Save" uses, so the same primary action reads the same way everywhere it
 * appears rather than this screen showing a flatter band than the rest of the app.
 * [BingeActionFooter.clearsNavigationBar] lets its background extend full-bleed behind the
 * gesture nav bar, leaving only the button itself to clear it.
 */
@Composable
private fun RequestPrimaryAction(
    detail: RequestDetail,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reviewable = detail.actions.canApprove || detail.actions.canRetry
    BingeActionFooter(
        label = stringResource(if (reviewable) R.string.request_primary_review else R.string.request_primary_manage),
        onClick = onClick,
        modifier = modifier,
        shape = BingeShapes.HeroTop,
        shadowElevation = dimensionResource(DesR.dimen.snackbar_elevation),
        horizontalPadding = resolvedContentInset(),
        clearsNavigationBar = true,
    )
}
