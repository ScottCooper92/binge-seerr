package com.cooper.binge.companion.seerr.ui.requests

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.BingeSheetNav
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.state.DialogMessage
import com.cooper.binge.companion.seerr.ui.state.dialogMessage
import com.cooper.binge.companion.seerr.ui.state.needsDialog
import com.cooper.binge.companion.seerr.feature.discover.R as DiscoverR

/**
 * A request's actions, and — where the caller has one — the server's media record beside them.
 *
 * The list's row sheet passes neither [media] nor [canEdit]: a row is a request and nothing else.
 * The detail page passes both, so the two objects it can act on live behind its one button.
 */
@Composable
internal fun RequestActionsSheet(
    item: RequestItem,
    actions: RequestActions,
    onApprove: () -> Unit,
    onRetry: () -> Unit,
    onDecline: (blockTitle: Boolean) -> Unit,
    onRemove: (blockTitle: Boolean) -> Unit,
    onDismiss: () -> Unit,
    canEdit: Boolean = false,
    onEdit: () -> Unit = {},
    media: MediaRecord? = null,
    mediaActions: ManageMediaActions? = null,
    requests: List<RequestSummary> = emptyList(),
    onMarkStatus: (is4k: Boolean) -> Unit = {},
    onOpenUser: ((Int) -> Unit)? = null,
    viewerId: Int? = null,
    canManageUsers: Boolean = false,
    detailLoad: SheetDetailLoad = SheetDetailLoad.Loaded,
    onRetryDetail: () -> Unit = {},
    edit: EditState? = null,
    editActions: EditRequestActions? = null,
    marking: MediaInstance? = null,
    onMarkSelect: (MediaStatusChoice) -> Unit = {},
    onMarkDismiss: () -> Unit = {},
    progress: ActionProgress? = null,
    actionsLanded: Int? = null,
    actionsLandedNow: () -> Int? = { null },
    titleRows: List<SheetTitleRow> = emptyList(),
    titleState: SheetStateChip? = null,
    titleBack: (() -> Unit)? = null,
    scope: SheetScope = SheetScope.Both,
    cards: RequestCardsHost? = null,
    destination: RequestDestination? = null,
) {
    var blockTitle by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<Pending?>(null) }
    // Whether the page for the edit or the choice of instance has been pushed. A page popped stays on screen until its exit ends, and
    // the state behind it is dropped only then, so the page underneath sees the state still open and must not push it again.
    val editPushed = remember { mutableStateOf(false) }
    val markPushed = remember { mutableStateOf(false) }
    // At the title's level the only Decline is a request card's, and a card's decision updates the card, not the sheet.
    val closesOnLanding = scope != SheetScope.Title
    CloseWhenActionLands(actionsLanded.takeIf { closesOnLanding }, { actionsLandedNow().takeIf { closesOnLanding } }, onDismiss)
    val shown = rememberOwnProgress(progress)
    // Over a title page the sheet has no Block row, so Decline and Remove offer it in their confirm, and Decline always asks.
    val blockInConfirm = titleState != null && actions.canBlock
    BingeAdaptiveSheet(
        kind = BingeSheetKind.Task,
        onDismissRequest = onDismiss,
        // The editor's state is the view model's, so Back from its page cancels the edit there.
        onPageBack = { page ->
            when (page) {
                EDIT_PAGE -> editActions?.onDismiss?.invoke()
                MARK_PAGE -> onMarkDismiss()
            }
        },
    ) { nav ->
        if (StackedPage(nav, item, edit, editActions, marking, onMarkSelect, editPushed, markPushed)) return@BingeAdaptiveSheet
        RequestActionsContent(
            model =
                RequestSheetModel(
                    item = item,
                    actions = actions,
                    canEdit = canEdit,
                    media = media,
                    viewerId = viewerId,
                    canManageUsers = canManageUsers,
                    detailLoad = detailLoad,
                    progress = shown,
                    titleRows = titleRows,
                    titleState = titleState,
                    titleBack = titleBack,
                    scope = scope,
                    destination = destination,
                    cards = cards?.let { RequestCardsModel(requests, item.id, it.onOpen, it.snackbar, it.onChanged) },
                ),
            callbacks =
                RequestSheetCallbacks(
                    onApprove = {
                        onApprove()
                        onDismiss()
                    },
                    onRetry = {
                        onRetry()
                        onDismiss()
                    },
                    onDecline = {
                        if (blockTitle || blockInConfirm) pending = Pending.Decline else onDecline(false)
                    },
                    onRemove = { pending = Pending.Remove },
                    // Stacked, as Binge's advanced-options picker opens over its request sheet: this sheet
                    // stays open underneath, and Back, a choice or a save returns to it rather than the page.
                    // Where the sheet stacks pages the editor is a page of it instead of a second sheet.
                    onEdit = onEdit,
                    onMarkStatus = onMarkStatus,
                    onDeleteFiles = { is4k -> pending = Pending.DeleteFiles(is4k) },
                    onClearData = { pending = Pending.ClearData },
                    onRetryDetail = onRetryDetail,
                    onOpenUser =
                        onOpenUser?.let { open ->
                            { id ->
                                onDismiss()
                                open(id)
                            }
                        },
                    onOpenServices = mediaActions?.onOpenServices.afterDismissing(onDismiss),
                ),
            blockTitle = blockTitle,
            onBlockTitleChange = { blockTitle = it },
        )
    }
    pending?.let { step ->
        PendingConfirm(
            step = step,
            item = item,
            media = media,
            mediaActions = mediaActions,
            requests = requests,
            blockTitle = blockTitle,
            onStep = { pending = it },
            onDecline = onDecline,
            onRemove = onRemove,
            onBlockTitleChange = if (blockInConfirm) ({ blockTitle = it }) else null,
        )
    }
}

/**
 * Draws the edit, or the choice of instance for Mark as, as a page of this sheet where it stacks pages, and says whether it did, so the
 * sheet draws nothing else. The page follows the edit, not the tap: an edit begun in a narrow window shows here once it widens. A stack
 * that survived a narrow window keeps its page, but there the editor is a sheet of its own, not this one.
 */
@Composable
private fun StackedPage(
    nav: BingeSheetNav,
    item: RequestItem,
    edit: EditState?,
    editActions: EditRequestActions?,
    marking: MediaInstance?,
    onMarkSelect: (MediaStatusChoice) -> Unit,
    editPushed: MutableState<Boolean>,
    markPushed: MutableState<Boolean>,
): Boolean {
    PushPageOnce(nav.stacksPages && editActions != null && edit != null, nav, EDIT_PAGE, editPushed)
    if (nav.page == EDIT_PAGE && nav.stacksPages && editActions != null) {
        EditRequestPage(nav = nav, item = item, edit = edit, actions = editActions)
        return true
    }
    PushPageOnce(nav.stacksPages && marking != null, nav, MARK_PAGE, markPushed)
    if (nav.page == MARK_PAGE && nav.stacksPages && marking != null) {
        MarkAsPage(nav = nav, instance = marking, onSelect = onMarkSelect)
        return true
    }
    return false
}

/** Pushes [page] once while [open], and not again while the page it pushed is still leaving: the state behind it outlives its exit. */
@Composable
private fun PushPageOnce(
    open: Boolean,
    nav: BingeSheetNav,
    page: String,
    pushed: MutableState<Boolean>,
) {
    LaunchedEffect(open, nav.page) {
        when {
            !open -> pushed.value = false
            nav.page == page -> pushed.value = true
            !pushed.value -> {
                pushed.value = true
                nav.push(page)
            }
        }
    }
}

private const val EDIT_PAGE = "edit"
private const val MARK_PAGE = "mark"

/** One instance's state choice as a page of the actions sheet. A pick is applied and the page steps back. */
@Composable
private fun MarkAsPage(
    nav: BingeSheetNav,
    instance: MediaInstance,
    onSelect: (MediaStatusChoice) -> Unit,
) {
    MediaStatusSheetContent(
        instance = instance,
        onSelect = { choice ->
            onSelect(choice)
            nav.pop()
        },
        dragHandle = false,
    )
}

/** The request editor as a page of the actions sheet, closing itself when the edit ends, saved or cancelled. */
@Composable
private fun EditRequestPage(
    nav: BingeSheetNav,
    item: RequestItem,
    edit: EditState?,
    actions: EditRequestActions,
) {
    LaunchedEffect(edit == null) { if (edit == null) nav.pop() }
    edit?.let { EditRequestContent(item = item, edit = it, actions = actions) }
}

/** The snackbar line for a moderation's outcome; null for one the sheet shows in place, which needs no snackbar. */
@StringRes
internal fun ModerationEvent.messageRes(): Int? =
    (
        when (this) {
            ModerationEvent.Approved -> R.string.request_approved
            ModerationEvent.Retried -> R.string.request_retried
            ModerationEvent.Edited -> R.string.request_edited
            ModerationEvent.Blocked -> DiscoverR.string.request_title_blocked
            ModerationEvent.BlockFailed -> R.string.request_block_failed
            ModerationEvent.MediaStatusSet -> R.string.media_status_set
            ModerationEvent.MediaCleared -> R.string.media_cleared
            ModerationEvent.MediaFilesDeleted -> R.string.media_files_deleted
            is ModerationEvent.MediaFilesAndRequestsDeleted -> R.string.media_files_and_requests_deleted
            is ModerationEvent.MediaFilesDeletedRequestsKept -> R.string.media_files_deleted_requests_kept
            ModerationEvent.MediaFilesNoDefaultServer -> null
            ModerationEvent.Declined -> R.string.request_declined
            ModerationEvent.DeclinedAndBlocked -> R.string.request_declined_and_blocked
            is ModerationEvent.DeclinedButBlockFailed -> null
            ModerationEvent.Removed -> R.string.request_removed
            ModerationEvent.RemovedAndBlocked -> R.string.request_removed_and_blocked
            is ModerationEvent.RemovedButBlockFailed -> null
            is ModerationEvent.Failed -> if (error.needsDialog()) null else R.string.request_action_failed
        }
    )

/**
 * The dialog for an outcome that gives a cause and a next step, so the user must read it to act; null for one that is a
 * snackbar line or shown in place. A half-done block and a sign-in the server rejected both qualify.
 */
internal fun ModerationEvent.dialogMessage(resources: Resources): DialogMessage? =
    when (this) {
        is ModerationEvent.DeclinedButBlockFailed ->
            DialogMessage(
                resources.getString(R.string.request_declined_block_failed_title),
                resources.getString(R.string.request_declined_block_failed_body),
            )
        is ModerationEvent.RemovedButBlockFailed ->
            DialogMessage(
                resources.getString(R.string.request_removed_block_failed_title),
                resources.getString(R.string.request_removed_block_failed_body),
            )
        is ModerationEvent.Failed -> error.takeIf { it.needsDialog() }?.dialogMessage(resources)
        else -> null
    }

internal fun ModerationEvent.isError(): Boolean =
    this is ModerationEvent.Failed ||
        this is ModerationEvent.DeclinedButBlockFailed ||
        this is ModerationEvent.RemovedButBlockFailed ||
        this is ModerationEvent.MediaFilesDeletedRequestsKept ||
        this == ModerationEvent.MediaFilesNoDefaultServer ||
        this == ModerationEvent.BlockFailed

/** [this] as a callback that first closes the sheet, as leaving it for another page does; null where there is no such page. */
private fun (() -> Unit)?.afterDismissing(onDismiss: () -> Unit): (() -> Unit)? =
    this?.let { open ->
        {
            onDismiss()
            open()
        }
    }
