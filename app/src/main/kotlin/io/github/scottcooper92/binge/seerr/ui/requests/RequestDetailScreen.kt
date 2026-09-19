package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeActionFooter
import com.binge.designsystem.component.BingeSnackbarHost
import com.binge.designsystem.component.DetailHero
import com.binge.designsystem.component.DetailOverlayTopBar
import com.binge.designsystem.component.ExpressiveIconButton
import com.binge.designsystem.component.IconButtonTone
import com.binge.designsystem.theme.BingeTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
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
    Scaffold(
        // Full-bleed: no top bar of the Scaffold's own, and the overlay bar clears the status bar
        // itself. What is left is the snackbar and the page's end clearing the navigation bar.
        snackbarHost = { BingeSnackbarHost(snackbarHostState, Modifier.windowInsetsPadding(pageEdgeInsets())) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                RequestDetailUiState.Loading -> LoadingScreen(Modifier.safeDrawingPadding())
                is RequestDetailUiState.Error ->
                    ErrorScreen(error = state.error, modifier = Modifier.safeDrawingPadding(), onRetry = actions.onRetry)
                is RequestDetailUiState.Ready -> Ready(state, actions)
            }
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
 * The page itself, with no sheet state of its own so a frame can render it.
 *
 * The hero draws no chrome; [DetailOverlayTopBar] floats over it with back and the two navigation
 * actions, and brings its scrim in as the hero's tail passes under it. A null [onOpen] or
 * [onReport] is an action this viewer, or this title, does not have.
 *
 * [RequestPrimaryAction] is pinned bottom-aligned rather than scrolled with the rest of the content
 * ([hasPrimaryAction]), so its measured height becomes the scroll's own bottom content padding —
 * the last section clears the footer instead of ending up underneath it. The no-action case renders
 * neither the footer nor that padding; the safe-drawing bottom inset it would otherwise have carried
 * goes straight back onto the scroll, exactly as it did before the footer existed.
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
    val inset = dimensionResource(DesR.dimen.screen_content_inset)
    val hasPrimaryAction = detail.hasPrimaryAction
    val density = LocalDensity.current
    var footerHeightPx by remember { mutableIntStateOf(0) }
    Box(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .then(
                        if (hasPrimaryAction) {
                            Modifier.padding(bottom = with(density) { footerHeightPx.toDp() })
                        } else {
                            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        },
                    ),
        ) {
            DetailHero(
                title = title,
                backdropUrl = detail.backdropUrl,
                tagline = null,
                metaText =
                    listOfNotNull(
                        stringResource(item.mediaType.labelRes()),
                        item.year,
                    ).joinToString(stringResource(R.string.hub_meta_separator)),
                onBack = onBack,
                showChrome = false,
                richBackdrop = true,
            )
            RequestHeadline(detail, Modifier.padding(inset), initiallyOverflowing)
            RequestFacts(detail, onOpenUser)
            RequestSections(detail)
            RequestSiblings(detail, onOpenSibling)
        }
        DetailOverlayTopBar(title = title, scrollState = scrollState, onBack = onBack) {
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
        }
        if (hasPrimaryAction) {
            RequestPrimaryAction(
                detail = detail,
                onClick = onPrimary,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { size -> footerHeightPx = size.height },
            )
        }
    }
}

/**
 * One action, pinned in a sheet-style footer over the page's own background. Its label follows the
 * request's state rather than being a generic "Manage": approving a pending request is the hottest
 * path here, and a label that does not say so buries it behind a tap on exactly what an admin opened
 * the app to do.
 *
 * [BingeActionFooter] defaults to a sheet's own container colour, which is invisible inside one; a
 * page passes [Color.Transparent] so its own background shows through instead of a banded surface
 * the page has no reason to show.
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
        containerColor = Color.Transparent,
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    )
}

/** The sides and the bottom of the window: what a page with no top bar and no Scaffold insets has to clear by hand. */
@Composable
private fun pageEdgeInsets(): WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
