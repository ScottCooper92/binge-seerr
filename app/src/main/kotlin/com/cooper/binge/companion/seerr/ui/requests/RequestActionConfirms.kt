package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.SwitchRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.isOpen

/**
 * Everything that changes something, in one sheet, behind the page's one button.
 *
 * Every irreversible choice here is a [Pending] rather than each having its own flag: the sheet had
 * two confirmation channels when it was two sheets, and one channel is what keeps a new destructive
 * action from arriving with a third.
 */
internal sealed interface Pending : java.io.Serializable {
    data object Decline : Pending

    data object Remove : Pending

    data object ClearData : Pending

    /** One instance's files, and whether its open requests go with them. */
    data class DeleteFiles(
        val is4k: Boolean,
        val removeRequests: Boolean = false,
    ) : Pending
}

/**
 * The second step every destructive choice takes. Declining asks only when it also blocks the
 * title, which is the part that outlives the request; removing and both media deletions always ask.
 */
@Composable
internal fun PendingConfirm(
    step: Pending,
    item: RequestItem,
    media: MediaRecord?,
    mediaActions: ManageMediaActions?,
    requests: List<RequestSummary>,
    blockTitle: Boolean,
    onStep: (Pending?) -> Unit,
    onDecline: (Boolean) -> Unit,
    onRemove: (Boolean) -> Unit,
) {
    when (step) {
        Pending.Decline, Pending.Remove ->
            RequestConfirm(
                removing = step == Pending.Remove,
                removeMessage = removeConfirmMessage(item, blockTitle, media.offersDeleteFiles(item)),
                onConfirm = { if (step == Pending.Remove) onRemove(blockTitle) else onDecline(blockTitle) },
                onStep = onStep,
            )
        Pending.ClearData ->
            BingeConfirmDialog(
                title = stringResource(R.string.media_clear_confirm_title),
                message = stringResource(R.string.media_clear_confirm_message),
                confirmLabel = stringResource(R.string.media_clear_data),
                destructive = true,
                onConfirm = {
                    media?.mediaId?.let { mediaActions?.onClearData?.invoke(it) }
                    onStep(null)
                },
                onDismissRequest = { onStep(null) },
            )
        is Pending.DeleteFiles ->
            DeleteFilesConfirm(step, media, requests.openRequestIds(step.is4k), onStep) { removeRequestIds ->
                media?.mediaId?.let { mediaActions?.onDeleteFiles?.invoke(it, step.is4k, removeRequestIds) }
            }
    }
}

@Composable
private fun RequestConfirm(
    removing: Boolean,
    removeMessage: String,
    onConfirm: () -> Unit,
    onStep: (Pending?) -> Unit,
) {
    BingeConfirmDialog(
        title = stringResource(if (removing) R.string.request_remove_confirm_title else R.string.request_decline_block_confirm_title),
        message = if (removing) removeMessage else stringResource(R.string.request_decline_block_confirm_message),
        confirmLabel = stringResource(if (removing) R.string.request_remove else R.string.request_decline),
        destructive = true,
        onConfirm = {
            onConfirm()
            onStep(null)
        },
        onDismissRequest = { onStep(null) },
    )
}

/**
 * One confirm, worded from what the server does: Radarr or Sonarr removes the title and the files it imported, and a
 * download still running is not stopped. The title's requests stay unless the switch is on, so where it has open ones
 * the confirm offers to remove them too. The sheet stays open, and its row says the files are gone.
 */
@Composable
private fun DeleteFilesConfirm(
    step: Pending.DeleteFiles,
    media: MediaRecord?,
    openRequestIds: List<Int>,
    onStep: (Pending?) -> Unit,
    onConfirm: (removeRequestIds: List<Int>) -> Unit,
) {
    val removing = step.removeRequests && openRequestIds.isNotEmpty()
    BingeConfirmDialog(
        title = stringResource(R.string.media_delete_files_confirm_title, stringResource(media.clientRes())),
        message = deleteFilesMessage(isTv = media?.isTv == true),
        confirmLabel = stringResource(if (removing) R.string.media_delete_files_and_requests else R.string.media_delete_files),
        destructive = true,
        onConfirm = {
            onConfirm(if (removing) openRequestIds else emptyList())
            onStep(null)
        },
        onDismissRequest = { onStep(null) },
        extraContent =
            openRequestIds.takeIf { it.isNotEmpty() }?.let { ids ->
                {
                    RemoveRequestsSwitch(
                        count = ids.size,
                        checked = step.removeRequests,
                        onCheckedChange = { onStep(step.copy(removeRequests = it)) },
                    )
                }
            },
    )
}

/** The confirm's body for a movie or a show: what Radarr or Sonarr removes, and that a running download carries on. */
@Composable
internal fun deleteFilesMessage(isTv: Boolean): String =
    if (isTv) {
        stringResource(R.string.media_delete_files_confirm_tv, stringResource(R.string.media_client_sonarr))
    } else {
        stringResource(R.string.media_delete_files_confirm_movie, stringResource(R.string.media_client_radarr))
    }

/** The switch to remove the instance's open requests with its files, so the title can be requested again. */
@Composable
internal fun RemoveRequestsSwitch(
    count: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SwitchRow(
        label = pluralStringResource(R.plurals.media_delete_files_remove_requests, count, count),
        supportingText = stringResource(R.string.media_delete_files_remove_requests_detail),
        checked = checked,
        onCheckedChange = onCheckedChange,
        supportingMaxLines = 3,
    )
}

private fun MediaRecord?.clientRes(): Int = if (this?.isTv == true) R.string.media_client_sonarr else R.string.media_client_radarr

/**
 * The ids of the requests that keep [is4k]'s instance requested: every one on it but those declined or completed, which
 * the server no longer counts against the title.
 */
internal fun List<RequestSummary>.openRequestIds(is4k: Boolean): List<Int> =
    filter { it.is4k == is4k && it.status.isOpen() }
        .map { it.id }
