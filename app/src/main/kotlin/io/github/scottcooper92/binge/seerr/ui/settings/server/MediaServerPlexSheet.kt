package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import com.binge.designsystem.R as DesR

/** The admin's own Plex servers, each connection a row: picking one fills the address fields. */
@Composable
internal fun PlexServerSheet(
    picker: PlexServerPicker,
    actions: MediaServerActions,
) {
    BingeBottomSheet(onDismissRequest = actions.onCloseServerPicker) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.screen_content_inset)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            Text(stringResource(R.string.server_settings_plex_pick), style = MaterialTheme.typography.titleLarge)
            when (picker) {
                PlexServerPicker.Loading -> LoadingScreen()
                is PlexServerPicker.Failed -> ErrorScreen(error = picker.error, onRetry = actions.onOpenServerPicker)
                is PlexServerPicker.Ready ->
                    if (picker.servers.isEmpty()) {
                        Text(stringResource(R.string.server_settings_plex_none), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        picker.servers.forEach { server -> PlexServerRows(server, actions) }
                    }
            }
        }
    }
}

@Composable
private fun PlexServerRows(
    server: PlexServerChoice,
    actions: MediaServerActions,
) {
    Text(server.name, style = MaterialTheme.typography.titleSmall)
    server.connections.forEach { connection ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { actions.onChooseConnection(server, connection) }
                    .padding(vertical = dimensionResource(DesR.dimen.padding_xs)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    stringResource(R.string.server_settings_plex_connection, connection.address, connection.port),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    listOfNotNull(
                        stringResource(if (connection.local) R.string.server_settings_plex_local else R.string.server_settings_plex_remote),
                        if (connection.useSsl) stringResource(R.string.server_settings_use_ssl) else null,
                    ).joinToString(stringResource(R.string.hub_meta_separator)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            connection.reachable?.let { reachable ->
                Text(
                    stringResource(if (reachable) R.string.server_settings_plex_reachable else R.string.server_settings_plex_unreachable),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (reachable) BingeSentiment.Positive.fill() else BingeSentiment.Negative.fill(),
                )
            }
        }
    }
}
