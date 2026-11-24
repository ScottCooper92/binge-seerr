package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.DestinationIcons
import com.binge.designsystem.R as DesR

/** Which destination field's picker is open over the form. */
internal enum class DestinationField { Server, Profile, RootFolder, Tags, RequestFor }

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
    // The three rows the read will fill, each holding its second line, so nothing grows or pops in when it lands.
    if (servers is ServersState.Loading) {
        return listOf(
            ListItem(DestinationIcons.server, label = title, clickable = false, loading = true, detailLoading = true),
            ListItem(
                DestinationIcons.profile,
                label = stringResource(R.string.advanced_profile),
                clickable = false,
                loading = true,
                detailLoading = true,
            ),
            ListItem(
                DestinationIcons.rootFolder,
                label = stringResource(R.string.advanced_root_folder),
                clickable = false,
                loading = true,
                detailLoading = true,
            ),
        )
    }
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
                ListItem(
                    DestinationIcons.profile,
                    label = stringResource(R.string.advanced_profile),
                    clickable = false,
                    loading = true,
                    detailLoading = true,
                ),
                ListItem(
                    DestinationIcons.rootFolder,
                    label = stringResource(R.string.advanced_root_folder),
                    clickable = false,
                    loading = true,
                    detailLoading = true,
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
                            ?.let { choices.choices.profileLabel(it, pick.anime) },
                    enabled = !state.submitting,
                    onClick = { onOpen(DestinationField.Profile) },
                ),
                ListItem(
                    DestinationIcons.rootFolder,
                    label = stringResource(R.string.advanced_root_folder),
                    detail = pick.rootFolder?.let { choices.choices.folderLabel(it) },
                    enabled = !state.submitting,
                    onClick = { onOpen(DestinationField.RootFolder) },
                ),
            ) +
                listOfNotNull(
                    ListItem(
                        DestinationIcons.tags,
                        label = stringResource(R.string.request_tags),
                        detail = choices.choices.tagsSummary(pick.shownTagIds()),
                        enabled = !state.submitting,
                        onClick = { onOpen(DestinationField.Tags) },
                    ).takeIf { choices.choices.tags.isNotEmpty() },
                )
    }

@Composable
internal fun failedRow(
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

/**
 * The form's only server row, for an administrator: Add a server, where the read holds none of this version, so there is no Server
 * page to reach. Otherwise Add a server is on the Server page; editing a server is Settings' job.
 */
@Composable
internal fun adminServerRows(
    state: RequestSheetUiState.Ready,
    actions: RequestSheetActions,
): List<ListItem> {
    if (!state.access.mayManageServers || !state.access.mayChooseDestination || state.servers !is ServersState.Ready) return emptyList()
    val add = actions.onAddServer ?: return emptyList()
    if (state.servers4k.isNotEmpty()) return emptyList()
    return listOf(
        ListItem(
            DestinationIcons.addServer,
            label = stringResource(R.string.request_sheet_add_server),
            enabled = !state.submitting,
            onClick = add,
        ),
    )
}

/** Under the Server page's choices, for an administrator: add another server. The sheet stays open under the new server's page. */
@Composable
internal fun ServerPageFooter(
    state: RequestSheetUiState.Ready,
    actions: RequestSheetActions,
) {
    if (!state.access.mayManageServers) return
    val rows =
        listOfNotNull(
            actions.onAddServer?.let { add ->
                ListItem(
                    DestinationIcons.addServer,
                    label = stringResource(R.string.request_sheet_add_server),
                    enabled = !state.submitting,
                    onClick = add,
                )
            },
        )
    if (rows.isEmpty()) return
    ItemGroup(
        title = null,
        rows = rows,
        modifier =
            Modifier
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m))
                .padding(top = dimensionResource(DesR.dimen.padding_m)),
    )
}
