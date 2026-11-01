package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
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
    callbacks: RequestSheetCallbacks,
): List<ListItem> {
    val error = MaterialTheme.colorScheme.error
    val client = stringResource(if (media.isTv) R.string.media_client_sonarr else R.string.media_client_radarr)
    return buildList {
        media.instances.forEach { instance ->
            add(instanceRow(instance, media.isTv, media.canSetStatus) { callbacks.onMarkStatus(instance.is4k) })
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

@Composable
private fun instanceRow(
    instance: MediaInstance,
    isTv: Boolean,
    canSetStatus: Boolean,
    onMarkStatus: () -> Unit,
): ListItem =
    ListItem(
        icon = instance.icon(isTv),
        label = stringResource(instance.labelRes()),
        // Where the status can be set the row is a picker, so its detail is its current value. Where it cannot, the
        // status is only information and keeps its chip.
        detail =
            if (canSetStatus) {
                instance.status?.let { stringResource(it.stateLabel(instance.download).labelRes) }
            } else {
                stringResource(R.string.media_state_not_downloading_detail).takeIf { instance.isProcessingWithoutDownload }
            },
        clickable = canSetStatus,
        trailingContent =
            instance.status?.takeIf { !canSetStatus }?.let { status -> { MediaStateChip(status = status, download = instance.download) } },
        onClick = onMarkStatus,
    )

/** Processing with nothing in the queue: a read-only row says why that may be. A picker's detail is its value. */
private val MediaInstance.isProcessingWithoutDownload: Boolean
    get() = status == SeerrMediaStatusCode.Processing && download == DownloadActivity.None

/**
 * Whether an instance in this state has files to delete. [MediaRecord.canDeleteFiles] is who may
 * delete at all; this is what there currently is to delete. `Processing` counts as a download
 * already in flight, and a null status (the server has nothing for this instance) does not.
 */
internal fun SeerrMediaStatusCode?.hasFiles(): Boolean =
    this == SeerrMediaStatusCode.Processing || this == SeerrMediaStatusCode.PartiallyAvailable || this == SeerrMediaStatusCode.Available
