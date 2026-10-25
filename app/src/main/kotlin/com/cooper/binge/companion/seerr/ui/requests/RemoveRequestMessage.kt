package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode

/**
 * Whether the request's version has a download running or files on disk. Removing a request leaves both: Seerr's
 * `DELETE /request/{id}` deletes the row and resets the media's status, and never calls Radarr or Sonarr (#1721).
 */
internal val RequestItem.keepsFilesOnRemove: Boolean
    get() = download != null || mediaStatus == SeerrMediaStatusCode.Available || mediaStatus == SeerrMediaStatusCode.PartiallyAvailable

/**
 * The Remove confirm's body. Where the version has a download or files, it says they stay, and names Delete files
 * only when [offersDeleteFiles]: the confirm never points at a control the user cannot reach from it.
 */
@Composable
internal fun removeConfirmMessage(
    item: RequestItem,
    blockTitle: Boolean,
    offersDeleteFiles: Boolean,
): String {
    val message = stringResource(if (blockTitle) R.string.request_remove_block_confirm_message else R.string.request_remove_confirm_message)
    if (!item.keepsFilesOnRemove) return message
    val note = stringResource(if (offersDeleteFiles) R.string.request_remove_files_stay_delete else R.string.request_remove_files_stay)
    return "$message $note"
}

/** Whether the sheet beside [item]'s confirm has a Delete files row for the request's version. */
internal fun MediaRecord?.offersDeleteFiles(item: RequestItem): Boolean =
    this != null &&
        canDeleteFiles &&
        instances.any { it.is4k == item.is4k && it.filesDeletion == null && it.status.hasFiles() }
