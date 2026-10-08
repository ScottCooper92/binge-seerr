package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.PeekingListSheet
import com.binge.designsystem.R as DesR

/** The admin's own Plex servers in a peeking sheet, a group of connection rows each: picking one fills the address. */
@Composable
internal fun PlexServerSheet(
    picker: PlexServerPicker,
    actions: MediaServerActions,
) {
    PeekingListSheet(title = stringResource(R.string.server_settings_plex_pick), onDismiss = actions.onCloseServerPicker) {
        PlexServerChoices(picker, actions)
    }
}

/** The sheet's body, apart from the sheet so a frame can render it. */
@Composable
internal fun PlexServerChoices(
    picker: PlexServerPicker,
    actions: MediaServerActions,
) {
    val inset = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m))
    when (picker) {
        PlexServerPicker.Loading -> ListLoading()
        is PlexServerPicker.Failed -> ErrorScreen(error = picker.error, onRetry = actions.onOpenServerPicker)
        is PlexServerPicker.Ready ->
            if (picker.servers.isEmpty()) {
                Text(stringResource(R.string.server_settings_plex_none), style = MaterialTheme.typography.bodyMedium, modifier = inset)
            } else {
                picker.servers.forEach { server ->
                    ItemGroup(
                        title = server.name,
                        modifier = inset.padding(bottom = dimensionResource(DesR.dimen.padding_m)),
                        rows = server.connections.map { connection -> connectionRow(server, connection, actions) },
                    )
                }
            }
    }
}

@Composable
private fun connectionRow(
    server: PlexServerChoice,
    connection: PlexConnection,
    actions: MediaServerActions,
): ListItem {
    val reachable =
        connection.reachable?.let { ok ->
            stringResource(if (ok) R.string.server_settings_plex_reachable else R.string.server_settings_plex_unreachable) to
                (if (ok) BingeSentiment.Positive else BingeSentiment.Negative)
        }
    return ListItem(
        icon = if (connection.local) Icons.Filled.Home else Icons.Filled.Cloud,
        label = stringResource(R.string.server_settings_plex_connection, connection.address, connection.port),
        detail =
            listOfNotNull(
                stringResource(if (connection.local) R.string.server_settings_plex_local else R.string.server_settings_plex_remote),
                stringResource(R.string.server_settings_use_ssl).takeIf { connection.useSsl },
            ).joinToString(stringResource(R.string.hub_meta_separator)),
        onClick = { actions.onChooseConnection(server, connection) },
        trailingContent =
            reachable?.let { (label, sentiment) ->
                { Text(label, style = MaterialTheme.typography.labelMedium, color = sentiment.fill()) }
            },
    )
}
