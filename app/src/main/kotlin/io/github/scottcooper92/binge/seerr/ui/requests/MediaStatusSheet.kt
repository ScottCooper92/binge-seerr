package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.binge.designsystem.component.BingeBottomSheet
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/**
 * Marking one instance's state. Its own sheet rather than a group inside Manage media, because the
 * pick was already terminal there — it applied and closed the whole sheet — so this is that mode
 * made visible rather than a new one.
 *
 * Sequential, never stacked: each sheet owns a window and a scrim, so the host closes Manage media
 * before opening this. There is nothing to come back to, since picking closes everything anyway.
 */
@Composable
internal fun MediaStatusSheet(
    instance: MediaInstance,
    onSelect: (MediaStatusChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    BingeBottomSheet(onDismissRequest = onDismiss) {
        MediaStatusSheetContent(
            instance = instance,
            onSelect = { choice ->
                onSelect(choice)
                onDismiss()
            },
        )
    }
}

@Composable
internal fun MediaStatusSheetContent(
    instance: MediaInstance,
    onSelect: (MediaStatusChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = MediaStatusChoice.entries.firstOrNull { it.code == instance.status }
    Column(modifier = modifier.padding(bottom = dimensionResource(DesR.dimen.padding_l))) {
        Header(instance)
        MediaStatusChoice.entries.forEach { choice ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = dimensionResource(DesR.dimen.min_touch_target))
                        .selectable(selected = choice == selected, role = Role.RadioButton, onClick = { onSelect(choice) })
                        .padding(horizontal = dimensionResource(DesR.dimen.padding_m), vertical = dimensionResource(DesR.dimen.padding_s)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
            ) {
                RadioButton(selected = choice == selected, onClick = null)
                Text(
                    text = stringResource(choice.labelRes()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Which instance is being marked, because a title the server holds twice has two of these sheets. */
@Composable
private fun Header(instance: MediaInstance) {
    Column(
        modifier =
            Modifier.padding(
                horizontal = dimensionResource(DesR.dimen.padding_m),
                vertical = dimensionResource(DesR.dimen.padding_s),
            ),
    ) {
        Text(
            text = stringResource(R.string.media_mark_as),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(if (instance.is4k) R.string.settings_service_4k else R.string.media_instance_standard),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.media_mark_as_scope_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
