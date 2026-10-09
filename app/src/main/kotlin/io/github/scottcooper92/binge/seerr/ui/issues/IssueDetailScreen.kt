package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.ListRowPoster
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.resolvedContentPadding
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.openInBrowser
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.OverflowDetailScaffold
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateChip
import io.github.scottcooper92.binge.seerr.ui.state.messageRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import com.binge.designsystem.R as DesR

class IssueDetailActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onDraftChange: (String) -> Unit,
    val onPostComment: () -> Unit,
    val onRetryOutbox: (Long) -> Unit,
    val onEditOutbox: (Long, String) -> Unit,
    val onDropOutbox: (Long) -> Unit,
    val onEditComment: (Int, String) -> Unit,
    val onDeleteComment: (Int) -> Unit,
    val onToggleStatus: () -> Unit,
    val onDeleteIssue: () -> Unit,
)

/**
 * One issue as a page: the title it is about, the report, the thread, and the composer. The
 * title itself opens in the server's web client, as the request page does.
 *
 * @param showBack false when this issue is the only thing in the detail pane beside the hub, where a
 * back arrow to the hub is redundant. Always true today — an issue is only ever stacked above the
 * Issues section — kept in step with the other detail-pane screens for when that changes.
 */
@Composable
fun IssueDetailScreen(
    state: IssueDetailUiState,
    events: Flow<IssueDetailEvent>,
    actions: IssueDetailActions,
    showBack: Boolean = true,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collectLatest { event ->
            if (event == IssueDetailEvent.IssueDeleted) {
                actions.onBack()
                return@collectLatest
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message =
                    when (event) {
                        IssueDetailEvent.CommentEdited -> resources.getString(R.string.issue_comment_edited)
                        IssueDetailEvent.CommentDeleted -> resources.getString(R.string.issue_comment_deleted)
                        IssueDetailEvent.IssueResolved -> resources.getString(R.string.issue_resolved)
                        IssueDetailEvent.IssueReopened -> resources.getString(R.string.issue_reopened)
                        IssueDetailEvent.IssueDeleted -> return@collectLatest
                        is IssueDetailEvent.Failed -> resources.getString(event.error.messageRes())
                    },
                kind = if (event is IssueDetailEvent.Failed) SnackbarMessageKind.Error else SnackbarMessageKind.Confirmation,
            )
        }
    }
    var managing by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val ready = state as? IssueDetailUiState.Ready
    val seeded = state as? IssueDetailUiState.Seeded
    OverflowDetailScaffold(
        title = ready?.detail?.item?.title ?: seeded?.item?.title ?: stringResource(R.string.issue_detail_title),
        onBack = actions.onBack.takeIf { showBack },
        snackbarHostState = snackbarHostState,
        showOverflow = ready != null,
        overflowContentDescription = stringResource(R.string.issue_manage_cd),
        onOverflowClick = { managing = true },
        overflowIcon = Icons.AutoMirrored.Filled.OpenInNew,
        leadingActions = {
            if (ready?.detail?.canDelete == true) {
                IconButton(onClick = { confirmingDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.issue_delete))
                }
            }
        },
    ) { inner ->
        when (state) {
            IssueDetailUiState.Loading -> IssueDetailSkeleton(Modifier.padding(inner))
            is IssueDetailUiState.Seeded -> Seeded(state, actions, contentPadding = inner)
            is IssueDetailUiState.Error ->
                ErrorScreen(error = state.error, modifier = Modifier.padding(inner), onRetry = actions.onRetry)
            is IssueDetailUiState.Ready -> Ready(state, events, actions, contentPadding = inner)
        }
    }
    if (managing && ready != null) {
        IssueManageSheet(
            detail = ready.detail,
            onDismiss = { managing = false },
        )
    }
    if (confirmingDelete) {
        BingeConfirmDialog(
            title = stringResource(R.string.issue_delete_confirm_title),
            message = stringResource(R.string.issue_delete_confirm_message),
            confirmLabel = stringResource(R.string.issue_delete),
            destructive = true,
            onConfirm = {
                confirmingDelete = false
                actions.onDeleteIssue()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/**
 * The tapped row's header while the issue loads, with the skeleton's own report and Comments heading under it;
 * a failed refresh swaps the rest of the page for the error and its retry, and the header stays.
 */
@Composable
private fun Seeded(
    state: IssueDetailUiState.Seeded,
    actions: IssueDetailActions,
    contentPadding: PaddingValues,
) {
    val inset = resolvedContentInset()
    val sides = resolvedContentPadding()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        IssueHeader(state.item, onOpen = null, modifier = Modifier.padding(resolvedContentPadding(top = inset, bottom = inset)))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(sides))
        if (state.error != null) {
            ErrorScreen(error = state.error, onRetry = actions.onRetry)
        } else {
            IssueBodySkeleton()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Ready(
    state: IssueDetailUiState.Ready,
    events: Flow<IssueDetailEvent>,
    actions: IssueDetailActions,
    contentPadding: PaddingValues,
) {
    val modals = rememberSaveable(saver = IssueModalState.Saver) { IssueModalState() }
    val detail = state.detail
    val context = LocalContext.current
    val inset = resolvedContentInset()
    val sides = resolvedContentPadding()
    // The thread scrolls under the top bar. The navigation bar's inset goes under the pinned bar where there is one, and into
    // the thread's scroll where there is not.
    val pinnedBar = detail.canComment || detail.canResolve
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(if (pinnedBar) PaddingValues(top = contentPadding.calculateTopPadding()) else contentPadding),
        ) {
            IssueHeader(
                detail.item,
                onOpen = { context.openInBrowser(detail.webUrl) },
                modifier = Modifier.padding(resolvedContentPadding(top = inset, bottom = inset)),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(sides))
            detail.report?.let { report ->
                Column(modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.OVERVIEW))) {
                    SectionHeader(title = stringResource(R.string.issue_problem))
                    Text(
                        text = report.message,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (detail.canActOn(report)) {
                                        Modifier.combinedClickable(
                                            onClick = { modals.openEdit(report) },
                                            onLongClick = { modals.actingOnCommentId = report.id },
                                            onLongClickLabel = stringResource(R.string.issue_comment_actions_cd),
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).padding(sides),
                    )
                }
            }
            SectionHeader(title = stringResource(R.string.issue_comments_title))
            IssueThread(
                thread = state.thread,
                detail = detail,
                commentAction = state.commentAction,
                now = System.currentTimeMillis(),
                onActOn = { comment -> modals.actingOnCommentId = comment.id },
                onTapOutbox = { entry -> modals.outboxActionId = entry.localId },
                modifier = Modifier.padding(sides).padding(bottom = dimensionResource(DesR.dimen.padding_l)),
            )
        }
        if (pinnedBar) {
            ActionBar(
                detail = detail,
                action = state.action,
                onAddComment = { modals.composing = true },
                onToggleStatus = { modals.confirmingStatus = true },
                sides = sides,
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
            )
        }
    }
    if (modals.confirmingStatus) {
        val resolving = detail.item.status == IssueStatus.Open
        BingeConfirmDialog(
            title = stringResource(if (resolving) R.string.issue_resolve_confirm_title else R.string.issue_reopen_confirm_title),
            message = stringResource(if (resolving) R.string.issue_resolve_confirm_message else R.string.issue_reopen_confirm_message),
            confirmLabel = stringResource(if (resolving) R.string.issue_action_resolve else R.string.issue_action_reopen),
            onConfirm = {
                modals.confirmingStatus = false
                actions.onToggleStatus()
            },
            onDismiss = { modals.confirmingStatus = false },
        )
    }
    IssueModals(state, events, actions, modals)
}

/** The title it is about: poster, title, what it affects, and the type and state; tapping opens the server's page. */
@Composable
private fun IssueHeader(
    item: IssueItem,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        Row(
            modifier = Modifier.fillMaxWidth().then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ListRowPoster(imageUrl = item.posterUrl, contentDescription = null)
            Spacer(Modifier.width(dimensionResource(DesR.dimen.list_row_gap)))
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.detail_cast_avatar_label_spacing))) {
                Text(
                    text = item.title ?: stringResource(item.mediaType.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        issueAffectedLabel(item)
                            ?: listOfNotNull(
                                stringResource(item.mediaType.labelRes()),
                                item.year,
                            ).joinToString(stringResource(R.string.hub_meta_separator)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(item.type.labelRes()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RequestStateChip(label = stringResource(item.status.labelRes()), tone = item.status.tone())
                }
            }
        }
    }
}
