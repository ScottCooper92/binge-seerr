package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
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
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeSnackbarHost
import com.binge.designsystem.component.DetailHero
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
        // Full-bleed: no top bar, and the hero pads its own controls clear of the status bar. What is
        // left to clear the navigation bar (and a landscape cutout) is the snackbar and the page's end.
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
    val item = detail.item
    var reporting by rememberSaveable { mutableStateOf(false) }
    var moderating by rememberSaveable { mutableStateOf(false) }
    var managing by rememberSaveable { mutableStateOf(false) }
    // Which instance the status sheet is marking, keyed by is4k because that is what tells the
    // two apart — and because a Boolean survives process death where MediaInstance would not.
    var marking by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val inset = dimensionResource(DesR.dimen.screen_content_inset)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(pageEdgeInsets())) {
        DetailHero(
            title = item.title ?: stringResource(item.mediaType.labelRes()),
            backdropUrl = detail.backdropUrl,
            tagline = null,
            metaText =
                listOfNotNull(
                    stringResource(item.mediaType.labelRes()),
                    item.year,
                ).joinToString(stringResource(R.string.hub_meta_separator)),
            onBack = actions.onBack,
            richBackdrop = true,
        )
        RequestHeadline(detail, Modifier.padding(inset))
        RequestFacts(detail)
        RequestSections(detail)
        RequestPageActions(
            detail = detail,
            onModerate = { moderating = true },
            onManage = { managing = true },
            onEdit = actions.onStartEdit,
            onReport = { reporting = true },
            modifier = Modifier.padding(inset),
        )
    }
    if (moderating) {
        RequestActionsSheet(
            item = item,
            actions = detail.actions,
            onApprove = actions.onApprove,
            onRetry = actions.onRetryRequest,
            onDecline = actions.onDecline,
            onRemove = actions.onRemove,
            onDismiss = { moderating = false },
        )
    }
    state.edit?.let { edit -> EditRequestSheet(item = item, edit = edit, actions = actions.edit) }
    val media = detail.media
    if (managing && media != null) {
        ManageMediaSheet(
            media = media,
            actions = actions.media,
            onMarkStatus = { is4k -> marking = is4k },
            onDismiss = { managing = false },
        )
    }
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

/** One button the page offers; the prominent one is the moderation sheet, where this viewer has it. */
private class RequestPageAction(
    @StringRes val labelRes: Int,
    val prominent: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * What this viewer may do with the request. Gathered into a list rather than four guarded buttons,
 * so "does the page offer anything at all" is the list being empty rather than the same four
 * conditions written again.
 */
@Composable
private fun RequestPageActions(
    detail: RequestDetail,
    onModerate: () -> Unit,
    onManage: () -> Unit,
    onEdit: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttons =
        buildList {
            if (detail.actions.any) add(RequestPageAction(R.string.request_actions_cd, prominent = true, onClick = onModerate))
            if (detail.media?.canManage == true) add(RequestPageAction(R.string.media_manage, onClick = onManage))
            if (detail.canEdit) add(RequestPageAction(R.string.request_edit_title, onClick = onEdit))
            if (detail.canReportIssue) add(RequestPageAction(R.string.issue_report_title, onClick = onReport))
        }
    if (buttons.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        buttons.forEach { button ->
            val label = stringResource(button.labelRes)
            if (button.prominent) {
                BingeFilledButton(label = label, onClick = button.onClick, modifier = Modifier.fillMaxWidth())
            } else {
                BingeOutlinedButton(label = label, onClick = button.onClick, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** The sides and the bottom of the window: what a page with no top bar and no Scaffold insets has to clear by hand. */
@Composable
private fun pageEdgeInsets(): WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
