package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.DestinationIcons

/** Which destination field's picker is open over the form. */
internal enum class DestinationField { Server, Profile, RootFolder }

/**
 * Server, profile and root folder, as rows of the options group for a user who may choose a destination. A read still going shows
 * a spinner in its row, and one that failed says so and a tap reads it again; the request waits only for a read still going, and a failed one still names the shown server.
 */
@Composable
internal fun destinationRows(
    state: RequestSheetUiState.Ready,
    actions: RequestSheetActions,
    onOpen: (DestinationField) -> Unit,
): List<ListItem> {
    if (!state.choosesDestination) return emptyList()
    val title = stringResource(R.string.advanced_server)
    val servers = state.servers
    if (servers is ServersState.Loading) return listOf(ListItem(DestinationIcons.server, label = title, clickable = false, loading = true))
    if (servers is ServersState.Failed) {
        return listOf(
            failedRow(DestinationIcons.server, stringResource(R.string.request_sheet_servers_failed), {
                actions.onRetryRead(RequestRead.Servers)
            }),
        )
    }
    val pick = state.pick ?: return emptyList()
    val server =
        ListItem(
            DestinationIcons.server,
            label = title,
            detail = state.servers4k.firstOrNull { it.id == pick.serverId }?.name,
            enabled = !state.submitting,
            onClick = { onOpen(DestinationField.Server) },
        )
    return listOf(server) + choiceRows(state, pick, actions, onOpen)
}

@Composable
private fun choiceRows(
    state: RequestSheetUiState.Ready,
    pick: DestinationPick,
    actions: RequestSheetActions,
    onOpen: (DestinationField) -> Unit,
): List<ListItem> =
    when (val choices = pick.choices) {
        ChoicesState.Loading ->
            listOf(
                ListItem(DestinationIcons.profile, label = stringResource(R.string.advanced_profile), clickable = false, loading = true),
                ListItem(
                    DestinationIcons.rootFolder,
                    label = stringResource(R.string.advanced_root_folder),
                    clickable = false,
                    loading = true,
                ),
            )
        ChoicesState.Failed ->
            listOf(
                failedRow(
                    DestinationIcons.profile,
                    stringResource(R.string.request_sheet_choices_failed),
                    { actions.onRetryRead(RequestRead.Choices) },
                ),
            )
        is ChoicesState.Ready ->
            listOf(
                ListItem(
                    DestinationIcons.profile,
                    label = stringResource(R.string.advanced_profile),
                    detail =
                        choices.choices.profiles
                            .firstOrNull { it.id == pick.profileId }
                            ?.label,
                    enabled = !state.submitting,
                    onClick = { onOpen(DestinationField.Profile) },
                ),
                ListItem(
                    DestinationIcons.rootFolder,
                    label = stringResource(R.string.advanced_root_folder),
                    detail = pick.rootFolder,
                    enabled = !state.submitting,
                    onClick = { onOpen(DestinationField.RootFolder) },
                ),
            )
    }

@Composable
private fun failedRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    onRetry: () -> Unit,
) = ListItem(
    icon,
    label = message,
    detailColor = MaterialTheme.colorScheme.error,
    detail = stringResource(R.string.action_try_again),
    onClick = onRetry,
)
