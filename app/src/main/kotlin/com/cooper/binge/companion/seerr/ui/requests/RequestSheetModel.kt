package com.cooper.binge.companion.seerr.ui.requests

import com.cooper.binge.companion.seerr.seerr.SeerrError

/**
 * What the sheet renders: a request always, and the server's media record where there is one.
 *
 * The list's row sheet has neither a record nor an edit — a row is a request and nothing else — so
 * both are defaulted away rather than the list constructing an empty half.
 */
internal class RequestSheetModel(
    val item: RequestItem,
    val actions: RequestActions,
    val canEdit: Boolean = false,
    val media: MediaRecord? = null,
    val viewerId: Int? = null,
    val canManageUsers: Boolean = false,
    val detailLoad: SheetDetailLoad = SheetDetailLoad.Loaded,
    val progress: ActionProgress? = null,
)

/**
 * The actions whose progress shows on the control that started them. [closesSheet] is whether the actions sheet goes when
 * one lands: Decline and Remove end the sheet's business with the request, Mark as leaves it open on the new status.
 */
enum class RequestAction(
    val closesSheet: Boolean,
) {
    Decline(closesSheet = true),
    Remove(closesSheet = true),
    MarkAs(closesSheet = false),
}

/**
 * [action] is running, or, with [failed], did not go through and may be tried again. [attempt] numbers the tries, so a sheet
 * opened after a failure can tell that failure from one of its own. [is4k] names the instance a Mark as is acting on.
 */
data class ActionProgress(
    val action: RequestAction,
    val failed: Boolean = false,
    val attempt: Int = 0,
    val is4k: Boolean = false,
)

/** Which action is running or failed, how many tries have been numbered, and how many have landed and closed the sheet. */
internal data class ActionTrack(
    val progress: ActionProgress? = null,
    val attempts: Int = 0,
    val landed: Int = 0,
)

/**
 * Whether the request's own detail has arrived behind a sheet opened from a preview. Until it has, the
 * Media group, the requester and Edit are absent, so the sheet says it is still working rather than growing silently.
 */
internal sealed interface SheetDetailLoad {
    data object Loaded : SheetDetailLoad

    data object Loading : SheetDetailLoad

    data class Failed(
        val error: SeerrError,
    ) : SheetDetailLoad
}

/** What its rows call back into, bundled so the content's parameter list stays readable. */
internal class RequestSheetCallbacks(
    val onApprove: () -> Unit,
    val onRetry: () -> Unit,
    val onDecline: () -> Unit,
    val onRemove: () -> Unit,
    val onEdit: () -> Unit = {},
    val onMarkStatus: (is4k: Boolean) -> Unit = {},
    val onDeleteFiles: (is4k: Boolean) -> Unit = {},
    val onClearData: () -> Unit = {},
    val onOpenUser: ((Int) -> Unit)? = null,
    val onRetryDetail: () -> Unit = {},
)
