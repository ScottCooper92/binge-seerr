package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.hasFiles
import com.cooper.binge.companion.seerr.ui.state.DownloadActivity
import com.cooper.binge.companion.seerr.ui.state.MediaStateChip
import com.cooper.binge.companion.seerr.ui.state.labelRes
import com.cooper.binge.companion.seerr.ui.state.stateLabel

/**
 * Each instance is one row carrying its state; where the status can be set the row opens the picker,
 * so "what it is" and "change it" are one thing rather than a label and a separate Mark as row.
 */
@Composable
internal fun mediaRows(
    media: MediaRecord,
    progress: ActionProgress?,
    callbacks: RequestSheetCallbacks,
): List<ListItem> {
    val error = MaterialTheme.colorScheme.error
    val client = stringResource(if (media.isTv) R.string.media_client_sonarr else R.string.media_client_radarr)
    return buildList {
        media.instances.forEach { instance ->
            val marking = progress?.takeIf { it.action == RequestAction.MarkAs && it.is4k == instance.is4k }
            add(instanceRow(instance, media.isTv, media.canSetStatus, marking) { callbacks.onMarkStatus(instance.is4k) })
        }
        // Every picker comes before the first delete, so the destructive rows run together at the end.
        if (media.canDeleteFiles) {
            // A deletion's result stays in place: the row that ran it says how it went, and offers it no more.
            media.instances.filter { it.filesDeletion != null || it.status.hasFiles() }.forEach { instance ->
                add(deleteFilesRow(instance, media, client, callbacks))
            }
        }
        if (media.canClearData) {
            add(
                ListItem(
                    icon = Icons.Filled.DeleteForever,
                    iconTint = error,
                    label = stringResource(if (media.clearing) R.string.media_clearing_data else R.string.media_clear_data),
                    detail = stringResource(R.string.media_clear_detail),
                    trailingContent = {},
                    destination = ListItemDestination.Action,
                    clickable = !media.clearing,
                    loading = media.clearing,
                    onClick = callbacks.onClearData,
                ),
            )
        }
    }
}

/**
 * One instance's row. Marking it keeps the row's label, height and place: running, the trailing slot is the loading indicator
 * and the row takes no taps; failed, the subtitle says what happened in place of the status, with Try again trailing, and a
 * tap opens the picker again.
 */
@Composable
private fun instanceRow(
    instance: MediaInstance,
    isTv: Boolean,
    canSetStatus: Boolean,
    marking: ActionProgress?,
    onMarkStatus: () -> Unit,
): ListItem {
    val running = marking != null && !marking.failed
    val failed = marking?.failed == true
    val error = MaterialTheme.colorScheme.error
    return ListItem(
        icon = instance.icon(isTv),
        label = stringResource(instance.labelRes()),
        // Where the status can be set the row is a picker, so its detail is its current value. Where it cannot, the
        // status is only information and keeps its chip.
        detail =
            when {
                failed -> stringResource(R.string.media_mark_failed_detail)
                canSetStatus -> instance.status?.let { stringResource(it.stateLabel(instance.download).labelRes) }
                else -> stringResource(R.string.media_state_not_downloading_detail).takeIf { instance.isProcessingWithoutDownload }
            },
        detailColor = error.takeIf { failed },
        loading = running,
        clickable = canSetStatus && !running,
        trailingContent =
            when {
                failed -> (
                    {
                        Text(
                            text = stringResource(R.string.action_try_again),
                            style = MaterialTheme.typography.labelLarge,
                            color = error,
                        )
                    }
                )
                canSetStatus -> null
                else -> instance.status?.let { status -> { MediaStateChip(status = status, download = instance.download) } }
            },
        onClick = onMarkStatus,
    )
}

/** Processing with nothing in the queue: a read-only row says why that may be. A picker's detail is its value. */
private val MediaInstance.isProcessingWithoutDownload: Boolean
    get() = status == SeerrMediaStatusCode.Processing && download == DownloadActivity.None
