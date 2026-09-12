package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeSnackbarHost
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.ListRowPoster
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import com.binge.designsystem.theme.BingeShapes
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.openInBrowser
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
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
 */
@Composable
fun IssueDetailScreen(
    state: IssueDetailUiState,
    events: Flow<IssueDetailEvent>,
    actions: IssueDetailActions,
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
    val ready = state as? IssueDetailUiState.Ready
    Scaffold(
        snackbarHost = { BingeSnackbarHost(snackbarHostState) },
        topBar = {
            BingeTopBar(
                title = ready?.detail?.item?.title ?: stringResource(R.string.issue_detail_title),
                onBack = actions.onBack,
                actions = {
                    if (ready != null) {
                        IconButton(onClick = { managing = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.issue_manage_cd))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                IssueDetailUiState.Loading -> LoadingScreen()
                is IssueDetailUiState.Error -> ErrorScreen(error = state.error, onRetry = actions.onRetry)
                is IssueDetailUiState.Ready -> Ready(state, actions)
            }
        }
    }
    if (managing && ready != null) {
        IssueManageSheet(
            detail = ready.detail,
            onDeleteIssue = actions.onDeleteIssue,
            onDismiss = { managing = false },
        )
    }
}

@Composable
private fun Ready(
    state: IssueDetailUiState.Ready,
    actions: IssueDetailActions,
) {
    val modals = rememberSaveable(saver = IssueModalState.Saver) { IssueModalState() }
    val detail = state.detail
    val inset = dimensionResource(DesR.dimen.screen_content_inset)
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            IssueHeader(detail, modifier = Modifier.padding(inset))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = inset))
            detail.report?.let { report ->
                SectionHeader(title = stringResource(R.string.issue_problem))
                Text(
                    text = report.message,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(if (detail.canActOn(report)) Modifier.clickable { modals.openEdit(report) } else Modifier)
                            .padding(horizontal = inset),
                )
            }
            SectionHeader(title = stringResource(R.string.issue_comments_title))
            IssueThread(
                thread = state.thread,
                detail = detail,
                commentAction = state.commentAction,
                now = System.currentTimeMillis(),
                onActOn = { comment -> modals.actingOnCommentId = comment.id },
                onTapOutbox = { entry -> modals.outboxActionId = entry.localId },
                modifier = Modifier.padding(horizontal = inset).padding(bottom = dimensionResource(DesR.dimen.padding_l)),
            )
        }
        if (detail.canComment || detail.canResolve) {
            ActionBar(
                detail = detail,
                action = state.action,
                onAddComment = { modals.composing = true },
                onToggleStatus = { modals.confirmingStatus = true },
                inset = inset,
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
    IssueModals(state, actions, modals)
}

/** The title it is about: poster, title, what it affects, and the type and state; tapping opens the server's page. */
@Composable
private fun IssueHeader(
    detail: IssueDetail,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val item = detail.item
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { context.openInBrowser(detail.webUrl) },
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            detail.mediaServerUrl?.let { url ->
                BingeOutlinedButton(label = stringResource(R.string.request_open_media_server), onClick = { context.openInBrowser(url) })
            }
            detail.serviceUrl?.let { url ->
                val labelRes = if (item.mediaType == RequestMediaType.Tv) R.string.media_open_sonarr else R.string.media_open_radarr
                BingeOutlinedButton(label = stringResource(labelRes), onClick = { context.openInBrowser(url) })
            }
        }
    }
}

/**
 * The pinned bar: a field that opens the composer, and Resolve or Reopen, each only where the user
 * may. A status change in flight spins its button and holds the field, so the two cannot race.
 */
@Composable
private fun ActionBar(
    detail: IssueDetail,
    action: IssueAction,
    onAddComment: () -> Unit,
    onToggleStatus: () -> Unit,
    inset: Dp,
) {
    val idle = action == IssueAction.None
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = inset, vertical = dimensionResource(DesR.dimen.padding_s)),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.list_row_gap)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (detail.canComment) ComposerField(onClick = onAddComment, enabled = idle, modifier = Modifier.weight(1f))
            if (detail.canResolve) {
                val resolving = detail.item.status == IssueStatus.Open
                BingeOutlinedButton(
                    label = stringResource(if (resolving) R.string.issue_action_resolve else R.string.issue_action_reopen),
                    onClick = onToggleStatus,
                    enabled = idle,
                    loading = action == IssueAction.UpdatingStatus,
                    modifier = if (detail.canComment) Modifier else Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ComposerField(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(BingeShapes.Pill)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(dimensionResource(DesR.dimen.hairline_thickness), MaterialTheme.colorScheme.outlineVariant, BingeShapes.Pill)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(
            text = stringResource(R.string.issue_comment_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IssueModals(
    state: IssueDetailUiState.Ready,
    actions: IssueDetailActions,
    modals: IssueModalState,
) {
    val detail = state.detail
    if (modals.composing) {
        CommentComposerSheet(
            draft = state.draft,
            onDraftChange = actions.onDraftChange,
            onSubmit = {
                actions.onPostComment()
                modals.composing = false
            },
            onDismiss = { modals.composing = false },
        )
    }
    modals.actingOnCommentId?.let { commentId ->
        val comment = (listOfNotNull(detail.report) + detail.comments).firstOrNull { it.id == commentId }
        if (comment == null) {
            modals.actingOnCommentId = null
        } else {
            CommentActionsSheet(
                onEdit = { modals.openEdit(comment) },
                onDelete = { modals.deletingCommentId = commentId },
                onDismiss = { modals.actingOnCommentId = null },
            )
        }
    }
    modals.editingCommentId?.let { commentId ->
        val original = (listOfNotNull(detail.report) + detail.comments).firstOrNull { it.id == commentId }?.message.orEmpty()
        // The sheet closes when the edit lands: the reloaded text matches the draft and nothing is in flight.
        val landed = state.commentAction == CommentAction.None && original.trim() == modals.editDraft.trim() && original.isNotEmpty()
        if (landed) {
            modals.editingCommentId = null
        } else {
            EditCommentSheet(
                title = stringResource(if (commentId == detail.report?.id) R.string.issue_edit_report else R.string.issue_edit_comment),
                draft = modals.editDraft,
                original = original,
                isSaving = state.commentAction == CommentAction.Editing(commentId),
                onDraftChange = { modals.editDraft = it },
                onSubmit = { actions.onEditComment(commentId, modals.editDraft) },
                onDismiss = { modals.editingCommentId = null },
            )
        }
    }
    modals.deletingCommentId?.let { commentId ->
        DeleteCommentDialog(
            onConfirm = {
                modals.deletingCommentId = null
                actions.onDeleteComment(commentId)
            },
            onDismiss = { modals.deletingCommentId = null },
        )
    }
    OutboxModals(state, actions, modals)
}

@Composable
private fun OutboxModals(
    state: IssueDetailUiState.Ready,
    actions: IssueDetailActions,
    modals: IssueModalState,
) {
    modals.outboxActionId?.let { localId ->
        state.outbox.firstOrNull { it.localId == localId }?.let { entry ->
            OutboxActionsSheet(
                retryable = (entry.state as? SendState.Failed)?.retryable == true,
                onRetry = { actions.onRetryOutbox(localId) },
                onEdit = { modals.openOutboxEdit(entry) },
                onDrop = { actions.onDropOutbox(localId) },
                onDismiss = { modals.outboxActionId = null },
            )
        }
    }
    modals.editingOutboxId?.let { localId ->
        EditCommentSheet(
            title = stringResource(R.string.issue_edit_comment),
            draft = modals.outboxEditDraft,
            original =
                state.outbox
                    .firstOrNull { it.localId == localId }
                    ?.message
                    .orEmpty(),
            isSaving = false,
            onDraftChange = { modals.outboxEditDraft = it },
            onSubmit = {
                actions.onEditOutbox(localId, modals.outboxEditDraft)
                modals.editingOutboxId = null
            },
            onDismiss = { modals.editingOutboxId = null },
        )
    }
}
