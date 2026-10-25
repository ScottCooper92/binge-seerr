package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.openInBrowser

/** One instance's Delete files row, or in its place what the deletion from this page came to. */
@Composable
internal fun deleteFilesRow(
    instance: MediaInstance,
    media: MediaRecord,
    client: String,
    callbacks: RequestSheetCallbacks,
): ListItem {
    val error = MaterialTheme.colorScheme.error
    return when (instance.filesDeletion) {
        FilesDeletion.Deleted ->
            ListItem(
                icon = Icons.Filled.DeleteSweep,
                label = stringResource(if (instance.is4k) R.string.media_4k_files_deleted else R.string.media_files_deleted),
                clickable = false,
                trailingContent = {},
            )
        // A setting in Seerr, so sending it again cannot help: the row says what to change, and opens it for an admin.
        FilesDeletion.NoDefaultServer -> {
            val context = LocalContext.current
            val url = media.servicesSettingsUrl
            ListItem(
                icon = Icons.Filled.ErrorOutline,
                iconTint = error,
                label = stringResource(if (instance.is4k) R.string.media_4k_files_not_deleted else R.string.media_files_not_deleted),
                detail =
                    stringResource(
                        if (instance.is4k) R.string.media_files_no_default_server_4k else R.string.media_files_no_default_server,
                        client,
                    ),
                detailColor = error,
                supportingMaxLines = NO_DEFAULT_SERVER_LINES,
                clickable = url != null,
                destination = ListItemDestination.External,
                trailingContent = if (url == null) ({}) else null,
                onClick = { url?.let(context::openInBrowser) },
            )
        }
        null ->
            ListItem(
                icon = Icons.Filled.DeleteSweep,
                iconTint = error,
                label = stringResource(if (instance.is4k) R.string.media_delete_4k_files else R.string.media_delete_files),
                detail = stringResource(R.string.media_delete_files_detail, client),
                trailingContent = {},
                destination = ListItemDestination.Action,
                onClick = { callbacks.onDeleteFiles(instance.is4k) },
            )
    }
}

/** The refusal's explanation is read in full, so its line runs as long as it needs at the largest font. */
private const val NO_DEFAULT_SERVER_LINES = 6
