package com.cooper.binge.companion.seerr.ui.tv.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.binge.designsystem.tv.focus.tvClickable
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.tv.component.BingeTvInitialsAvatar
import com.binge.designsystem.tv.component.TvConfirmDialog
import com.binge.designsystem.tv.component.TvIconButton
import com.binge.designsystem.tv.component.TvInfoCard
import com.binge.designsystem.tv.component.TvSideSheet
import com.binge.designsystem.tv.component.TvSideSheetBody
import com.binge.designsystem.tv.component.TvSideSheetRow
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.binge.designsystem.tv.theme.TvButtonStyle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.tv.jellyfinTvInstalled
import com.cooper.binge.companion.seerr.ui.tv.openInJellyfinTv
import com.cooper.binge.companion.seerr.ui.issues.IssueAction
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailEvent
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailUiState
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailViewModel
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.cooper.binge.companion.seerr.ui.issues.ThreadEntry
import com.cooper.binge.companion.seerr.ui.requests.labelRes
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/**
 * PROTOTYPE: one issue as a side sheet over the Issues hub, after YouTube's TV comment thread: the report flat at the
 * top, its actions as a row of round buttons, a reply hand-off, then the comments as cards.
 */
@Composable
internal fun TvIssueSheetEntry(
    issueId: Int,
    onDone: () -> Unit,
    viewModel: IssueDetailViewModel =
        hiltViewModel<IssueDetailViewModel, IssueDetailViewModel.Factory>(creationCallback = { it.create(issueId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    LaunchedEffect(viewModel) { viewModel.events.collect { if (it == IssueDetailEvent.IssueDeleted) onDone() } }
    TvSideSheet(onDismissRequest = onDone, toBottomEdge = true) { entryFocus ->
        when (val s = state) {
            is IssueDetailUiState.Ready -> IssueThread(
                    s,
                    entryFocus,
                    onToggle = viewModel::toggleStatus,
                    onDelete = viewModel::deleteIssue,
                    onEdit = viewModel::editComment,
                    onDeleteComment = viewModel::deleteComment,
                )
            is IssueDetailUiState.Error -> {
                TvSideSheetTitle(stringResource(R.string.issue_detail_title))
                TvSideSheetBody(stringResource(R.string.tv_list_load_failed))
                TvSideSheetRow(
                    label = stringResource(R.string.hub_retry),
                    icon = Icons.Filled.Refresh,
                    onClick = viewModel::reload,
                    modifier = Modifier.focusRequester(entryFocus),
                )
            }
            else -> {
                TvSideSheetTitle(stringResource(R.string.issue_detail_title))
                com.binge.designsystem.tv.component.TvSideSheetStepFocus(entryFocus)
                Text(
                    text = stringResource(R.string.tv_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.focusRequester(entryFocus).focusable(),
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.IssueThread(
    state: IssueDetailUiState.Ready,
    entryFocus: androidx.compose.ui.focus.FocusRequester,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (commentId: Int, message: String) -> Unit,
    onDeleteComment: (commentId: Int) -> Unit,
) {
    val item = state.detail.item
    // PROTOTYPE: the report opens a page of its own in the sheet, with the whole text and, where it is theirs, an edit.
    // Any card, the report or a comment, opens a page of its own: the whole text, then what the viewer may do to it.
    var openedId by remember { mutableStateOf<Int?>(null) }
    // Where focus goes whenever the thread shows: the report on arrival, else whatever the viewer left it for.
    val requesters = remember { mutableMapOf<String, androidx.compose.ui.focus.FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { androidx.compose.ui.focus.FocusRequester() }
    var returnTo by remember { mutableStateOf<String?>(null) }
    // Replying and editing need a keyboard, which a television does without: both hand off to the issue's web page.
    var handingOff by remember { mutableStateOf(false) }
    if (handingOff) {
        HandOffPage(
            title = item.title ?: stringResource(R.string.issue_detail_title),
            url = state.detail.webUrl,
            serverName = state.detail.serverName,
            onBack = { handingOff = false },
        )
        return
    }
    val report = state.detail.report
    val opened = openedId?.let { id -> (listOfNotNull(report) + state.detail.comments).firstOrNull { it.id == id } }
    if (opened != null) {
        val isReport = opened.id == report?.id
        ReportPage(
            title = item.title ?: stringResource(R.string.issue_detail_title),
            message = opened.message,
            // Read-only here: a comment the viewer may change is changed on the phone.
            onHandOff = if (state.detail.canActOn(opened)) ({ handingOff = true }) else null,
            // The report goes only with its issue, which the thread's own Delete does; a comment can go on its own.
            onDelete =
                if (!isReport && state.detail.canActOn(opened)) {
                    {
                        // Focus moves on to the comment after it, else the one before, else the reply row.
                        val ids = state.detail.comments.map { it.id }
                        val at = ids.indexOf(opened.id)
                        returnTo = (ids.getOrNull(at + 1) ?: ids.getOrNull(at - 1))?.let { "comment:$it" } ?: KEY_REPLY
                        onDeleteComment(opened.id)
                        openedId = null
                    }
                } else {
                    null
                },
            onBack = { openedId = null },
        )
        return
    }
    // Composed afresh each time the thread shows, so it lands focus on arrival and on every return from a page.
    val arrival = returnTo ?: if (report != null) KEY_REPORT else KEY_ACTIONS
    LaunchedEffect(Unit) { runCatchingFocus(requester(arrival)) }
    val separator = stringResource(R.string.hub_meta_separator)
    var confirmingDelete by remember { mutableStateOf(false) }
    if (confirmingDelete) {
        TvConfirmDialog(
            title = stringResource(R.string.issue_delete_confirm_title),
            message = stringResource(R.string.issue_delete_confirm_message),
            confirmLabel = stringResource(R.string.issue_delete),
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismissRequest = { confirmingDelete = false },
            destructive = true,
        )
    }
    TvSideSheetTitle(item.title ?: stringResource(R.string.issue_detail_title))
    Column(
        modifier =
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = dimensionResource(TvR.dimen.tv_overscan_vertical)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_sm)),
    ) {
        // The report, flat: who and when, what kind of problem and where, then what they wrote.
        val kind =
            listOfNotNull(
                stringResource(item.type.labelRes()),
                item.problemSeason?.takeIf { it > 0 }?.let { s -> item.problemEpisode?.takeIf { it > 0 }?.let { e -> "S$s · E$e" } ?: "S$s" },
                stringResource(if (item.status == IssueStatus.Open) R.string.issue_state_open else R.string.issue_state_resolved),
            ).joinToString(separator)
        (report?.message ?: item.problem)?.takeIf { it.isNotBlank() }?.let { text ->
            CommentCard(
                author = item.reportedBy.orEmpty(),
                line = listOfNotNull(item.reportedBy, formatRelativeOrAbsolute(item.createdAtMillis, state.now)).joinToString(separator),
                message = text,
                isMine = report?.isMine == true,
                kind = kind,
                maxLines = REPORT_PREVIEW_LINES,
                onClick = report?.let { r ->
                    {
                        returnTo = KEY_REPORT
                        openedId = r.id
                    }
                },
                avatarUrl = item.reportedByAvatarUrl,
                modifier = Modifier.focusRequester(requester(KEY_REPORT)),
            )
        }
        // The issue's actions, as YouTube's like, dislike and report: round buttons naming themselves on focus.
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s), Alignment.CenterHorizontally),
            // Left anywhere else on the sheet closes it; in the row it steps back a button, and closes only from the first.
            modifier =
                Modifier.fillMaxWidth().androidx_onKeyEventLeft {
                    focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Left)
                },
        ) {
            val busy = state.action != IssueAction.None
            if (state.detail.canResolve) {
                val resolving = item.status == IssueStatus.Open
                TvIconButton(
                    icon = if (resolving) Icons.Filled.CheckCircle else Icons.Filled.Refresh,
                    label = stringResource(if (resolving) R.string.issue_action_resolve else R.string.issue_action_reopen),
                    onClick = onToggle,
                    loading = state.action == IssueAction.UpdatingStatus,
                    enabled = !busy || state.action == IssueAction.UpdatingStatus,
                    modifier = Modifier.focusRequester(requester(KEY_ACTIONS)),
                )
            }
            // For an admin checking the report: the title itself, in the media server's own TV app.
            val context = androidx.compose.ui.platform.LocalContext.current
            state.detail.mediaServerItemId?.takeIf { context.jellyfinTvInstalled() }?.let { itemId ->
                TvIconButton(
                    icon = Icons.Filled.PlayCircle,
                    label = stringResource(R.string.tv_issue_open_in_proto, state.detail.mediaServerName ?: "Jellyfin"),
                    onClick = { context.openInJellyfinTv(itemId) },
                    modifier = if (state.detail.canResolve) Modifier else Modifier.focusRequester(requester(KEY_ACTIONS)),
                )
            }
            if (state.detail.canDelete) {
                TvIconButton(
                    icon = Icons.Filled.Delete,
                    label = stringResource(R.string.issue_delete),
                    onClick = { confirmingDelete = true },
                    style = TvButtonStyle.DestructiveOutline,
                    modifier =
                        if (state.detail.canResolve || state.detail.mediaServerItemId != null) Modifier else Modifier.focusRequester(requester(KEY_ACTIONS)),
                )
            }
        }
        // Where YouTube puts "Reply as": replying needs a keyboard, so the TV points at the phone.
        if (state.detail.canComment) {
            TvSideSheetRow(
                label = stringResource(R.string.tv_issue_reply_on_phone_proto),
                icon = Icons.AutoMirrored.Filled.Chat,
                onClick = {
                    returnTo = KEY_REPLY
                    handingOff = true
                },
                modifier = Modifier.focusRequester(requester(KEY_REPLY)),
            )
        }
        state.thread.forEach { entry ->
            when (entry) {
                is ThreadEntry.Server ->
                    CommentCard(
                        author = entry.comment.author.orEmpty(),
                        line = listOfNotNull(entry.comment.author, formatRelativeOrAbsolute(entry.comment.createdAtMillis, state.now))
                            .joinToString(separator),
                        message = entry.comment.message,
                        isMine = entry.comment.isMine,
                        maxLines = REPORT_PREVIEW_LINES,
                        onClick = {
                            returnTo = "comment:${entry.comment.id}"
                            openedId = entry.comment.id
                        },
                        modifier = Modifier.focusRequester(requester("comment:${entry.comment.id}")),
                    )
                else -> Unit
            }
        }
    }
}

@Composable
private fun Author(
    name: String,
    avatarUrl: String?,
    line: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_sm)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BingeTvInitialsAvatar(name = name, size = dimensionResource(TvR.dimen.tv_button_icon) + dimensionResource(DesR.dimen.padding_xs), avatarUrl = avatarUrl)
        Text(text = line, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A comment as YouTube draws one: the author and age over the text, filling light when focused. As on the phone, the
 * user's own sit at the end, narrower, tinted and squared at the top end; everyone else's at the start.
 */
@Composable
private fun CommentCard(
    author: String,
    line: String,
    message: String,
    isMine: Boolean,
    modifier: Modifier = Modifier,
    kind: String? = null,
    maxLines: Int = Int.MAX_VALUE,
    onClick: (() -> Unit)? = null,
    avatarUrl: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val rounded = dimensionResource(DesR.dimen.padding_m)
    val pointer = dimensionResource(DesR.dimen.padding_xs)
    val shape =
        androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = if (isMine) rounded else pointer,
            topEnd = if (isMine) pointer else rounded,
            bottomEnd = rounded,
            bottomStart = rounded,
        )
    val resting = if (isMine) scheme.primaryContainer else scheme.surfaceVariant.copy(alpha = 0.5f)
    val ink = if (focused) scheme.surface else if (isMine) scheme.onPrimaryContainer else scheme.onSurface
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (focused) scheme.onSurface else resting)
                    .then(modifier)
                    .then(
                        if (onClick != null) {
                            Modifier.tvClickable(onFocusChanged = { focused = it }, onClick = onClick)
                        } else {
                            Modifier.onFocusChanged { focused = it.isFocused }.focusable()
                        },
                    ).semantics(mergeDescendants = true) {}
                    .padding(dimensionResource(DesR.dimen.padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            // The user's own: only who and when move to the end; the text reads from the start like everyone else's.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_sm)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val avatar = @Composable {
                    BingeTvInitialsAvatar(
                        name = author,
                        size = dimensionResource(TvR.dimen.tv_button_icon) + dimensionResource(DesR.dimen.padding_xs),
                        avatarUrl = avatarUrl,
                    )
                }
                avatar()
                Text(
                    text = line,
                    style = MaterialTheme.typography.titleSmall,
                    color =
                        when {
                            focused -> scheme.surface
                            isMine -> scheme.onPrimaryContainer.copy(alpha = 0.75f)
                            else -> scheme.onSurfaceVariant
                        },
                )
            }
            kind?.let {
                Text(text = it, style = MaterialTheme.typography.labelLarge, color = if (focused) scheme.surface else scheme.primary)
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = ink,
                maxLines = maxLines,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/** How wide a comment card may run, so the two sides read as two sides. */
private const val MINE_WIDTH_FRACTION = 0.85f

/** How much of the report the thread shows before it asks to be opened. */
private const val REPORT_PREVIEW_LINES = 4
private const val KEY_REPORT = "report"
private const val KEY_ACTIONS = "actions"
private const val KEY_REPLY = "reply"

/** Offers focus to the report card once it has laid out; a sheet still arriving may not have it yet. */
private suspend fun runCatchingFocus(requester: androidx.compose.ui.focus.FocusRequester) {
    androidx.compose.runtime.withFrameNanos { }
    com.binge.companion.sdk.attempt { requester.requestFocus() }
}

/** Left on a focused child: [move] tries the step and says whether it went; Left that did not move is left for the sheet. */
private fun Modifier.androidx_onKeyEventLeft(move: () -> Boolean): Modifier =
    this.then(
        Modifier.onKeyEvent { event ->
            event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && move()
        },
    )

/** PROTOTYPE: a report or comment in full on a page of its own, read-only; changing it is a hand-off to the phone. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ReportPage(
    title: String,
    message: String,
    onHandOff: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    var confirmingDelete by remember { mutableStateOf(false) }
    if (confirmingDelete && onDelete != null) {
        TvConfirmDialog(
            title = stringResource(R.string.issue_delete_comment_title),
            message = stringResource(R.string.issue_delete_comment_message),
            confirmLabel = stringResource(R.string.issue_delete_comment),
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismissRequest = { confirmingDelete = false },
            destructive = true,
        )
    }
    TvSideSheetTitle(title)
    Column(
        modifier =
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = dimensionResource(TvR.dimen.tv_overscan_vertical)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.focusRequester(first).focusable(),
        )
        onHandOff?.let {
            TvSideSheetRow(
                label = stringResource(R.string.tv_issue_edit_on_phone_proto),
                icon = Icons.AutoMirrored.Filled.Chat,
                onClick = it,
            )
        }
        // Destructive, so last, and behind a confirm.
        onDelete?.let {
            TvSideSheetRow(
                label = stringResource(R.string.issue_delete_comment),
                icon = Icons.Filled.Delete,
                destructive = true,
                onClick = { confirmingDelete = true },
            )
        }
    }
    LaunchedEffect(Unit) { runCatchingFocus(first) }
}

/** PROTOTYPE: the issue's own page on the server's web client, as a code the phone scans to reply or edit there. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.HandOffPage(
    title: String,
    url: String,
    serverName: String,
    onBack: () -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val code = remember { androidx.compose.ui.focus.FocusRequester() }
    TvSideSheetTitle(title)
    TvSideSheetBody(stringResource(R.string.tv_issue_hand_off_body_proto, serverName))
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = dimensionResource(TvR.dimen.tv_overscan_vertical)),
        contentAlignment = Alignment.Center,
    ) {
        com.binge.designsystem.tv.component.TvQrCode(
            content = url,
            contentDescription = stringResource(R.string.tv_issue_hand_off_body_proto, serverName),
            modifier =
                Modifier
                    .focusRequester(code)
                    .focusable()
                    .size(dimensionResource(R.dimen.tv_bug_report_code_size)),
        )
    }
    LaunchedEffect(Unit) { runCatchingFocus(code) }
}


