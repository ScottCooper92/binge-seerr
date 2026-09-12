package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeOutlinedButton
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.ChoicePicker
import io.github.scottcooper92.binge.seerr.ui.state.MediaStateChip
import com.binge.designsystem.R as DesR

class ManageMediaActions(
    val onSetStatus: (mediaId: Int, status: MediaStatusChoice, is4k: Boolean) -> Unit,
    val onClearData: (mediaId: Int) -> Unit,
    val onDeleteFiles: (mediaId: Int, is4k: Boolean) -> Unit,
)

/** Which destructive step is awaiting its confirmation; deleting files asks twice, the second time naming the client. */
private sealed interface Confirm {
    data object Clear : Confirm

    data class DeleteFiles(
        val is4k: Boolean,
        val second: Boolean = false,
    ) : Confirm
}

/**
 * The server's record of the title, per instance: its state, the moderator's marks, Tautulli's
 * plays, and the file deletion. Clearing the record sits last, since it takes the request with it.
 */
@Composable
internal fun ManageMediaSheet(
    media: MediaRecord,
    actions: ManageMediaActions,
    onDismiss: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }
    BingeBottomSheet(onDismissRequest = onDismiss) {
        ManageMediaContent(
            media = media,
            onSetStatus = { status, is4k ->
                actions.onSetStatus(media.mediaId, status, is4k)
                onDismiss()
            },
            onDeleteFiles = { is4k -> confirm = Confirm.DeleteFiles(is4k) },
            onClearData = { confirm = Confirm.Clear },
        )
    }
    val client = stringResource(if (media.isTv) R.string.media_client_sonarr else R.string.media_client_radarr)
    when (val step = confirm) {
        null -> Unit
        Confirm.Clear ->
            BingeConfirmDialog(
                title = stringResource(R.string.media_clear_confirm_title),
                message = stringResource(R.string.media_clear_confirm_message),
                confirmLabel = stringResource(R.string.media_clear_data),
                destructive = true,
                onConfirm = {
                    confirm = null
                    actions.onClearData(media.mediaId)
                    onDismiss()
                },
                onDismiss = { confirm = null },
            )
        is Confirm.DeleteFiles ->
            BingeConfirmDialog(
                title =
                    if (step.second) {
                        stringResource(R.string.media_delete_files_second_title, client)
                    } else {
                        stringResource(R.string.media_delete_files_confirm_title)
                    },
                message =
                    if (step.second) {
                        stringResource(R.string.media_delete_files_second_message, client)
                    } else {
                        stringResource(R.string.media_delete_files_confirm_message)
                    },
                confirmLabel = stringResource(R.string.media_delete_files),
                destructive = true,
                onConfirm = {
                    if (step.second) {
                        confirm = null
                        actions.onDeleteFiles(media.mediaId, step.is4k)
                        onDismiss()
                    } else {
                        confirm = step.copy(second = true)
                    }
                },
                onDismiss = { confirm = null },
            )
    }
}

@Composable
internal fun ManageMediaContent(
    media: MediaRecord,
    onSetStatus: (MediaStatusChoice, Boolean) -> Unit,
    onDeleteFiles: (Boolean) -> Unit,
    onClearData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                .padding(bottom = dimensionResource(DesR.dimen.padding_l)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(stringResource(R.string.media_manage), style = MaterialTheme.typography.titleMedium)
        media.instances.forEach { instance ->
            MediaInstanceSection(
                instance = instance,
                media = media,
                onSetStatus = { onSetStatus(it, instance.is4k) },
                onDeleteFiles = { onDeleteFiles(instance.is4k) },
            )
        }
        if (media.canClearData) {
            BingeOutlinedButton(
                label = stringResource(R.string.media_clear_data),
                onClick = onClearData,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MediaInstanceSection(
    instance: MediaInstance,
    media: MediaRecord,
    onSetStatus: (MediaStatusChoice) -> Unit,
    onDeleteFiles: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            Text(
                stringResource(if (instance.is4k) R.string.settings_service_4k else R.string.media_instance_standard),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            instance.status?.let { MediaStateChip(status = it) }
        }
        if (media.canSetStatus) {
            ChoicePicker(
                title = stringResource(R.string.media_mark_as),
                choices = MediaStatusChoice.entries.map { it to stringResource(it.labelRes()) },
                selected = MediaStatusChoice.entries.firstOrNull { it.code == instance.status },
                onSelect = onSetStatus,
            )
        }
        instance.watch?.let { WatchDataText(it) }
        if (media.canDeleteFiles) {
            BingeOutlinedButton(
                label = stringResource(R.string.media_delete_files),
                onClick = onDeleteFiles,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WatchDataText(watch: WatchStats) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs))) {
        Text(stringResource(R.string.media_watch_title), style = MaterialTheme.typography.labelLarge)
        Text(
            stringResource(R.string.media_watch_plays, watch.playCount, watch.playCount7Days, watch.playCount30Days),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (watch.users.isNotEmpty()) {
            Text(
                stringResource(R.string.media_watch_users, watch.users.joinToString(stringResource(R.string.hub_meta_separator))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun MediaStatusChoice.labelRes(): Int =
    when (this) {
        MediaStatusChoice.Available -> R.string.media_state_available
        MediaStatusChoice.PartiallyAvailable -> R.string.media_state_partially_available
        MediaStatusChoice.Processing -> R.string.media_state_processing
        MediaStatusChoice.Pending -> R.string.request_state_pending
        MediaStatusChoice.Unknown -> R.string.media_state_unknown
    }
