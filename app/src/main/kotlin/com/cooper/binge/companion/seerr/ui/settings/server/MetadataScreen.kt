package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.state.RequestStateChip
import com.cooper.binge.companion.seerr.ui.state.RequestStateTone
import com.cooper.binge.companion.seerr.ui.users.settings.EditorActions
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEvent
import com.cooper.binge.companion.seerr.ui.users.settings.EditorPage
import com.cooper.binge.companion.seerr.ui.users.settings.ExtrasEditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.choiceSettingItem
import com.cooper.binge.companion.seerr.ui.users.settings.toEditorUiState
import kotlinx.coroutines.flow.Flow

/**
 * The metadata page as the web client lays it out: each provider's status from the last test, with the row that runs
 * one, then the provider for series and the one for anime. A test reaches the providers the draft would use.
 */
@Composable
fun MetadataScreen(
    state: ExtrasEditorUiState<MetadataForm, MetadataExtras>,
    events: Flow<EditorEvent>,
    actions: EditorActions<MetadataForm>,
    onTest: () -> Unit,
) {
    val extras = (state as? ExtrasEditorUiState.Ready<MetadataForm, MetadataExtras>)?.extras ?: MetadataExtras()
    EditorPage(
        title = stringResource(R.string.server_settings_metadata),
        state = state.toEditorUiState(),
        events = events,
        actions = actions,
        saveAsMade = true,
    ) { draft, enabled ->
        ItemGroup(
            title = stringResource(R.string.server_settings_metadata_status),
            rows =
                listOf(
                    statusItem(MetadataProvider.Tmdb, extras.tmdb),
                    statusItem(MetadataProvider.Tvdb, extras.tvdb),
                    ListItem(
                        icon = Icons.Filled.NetworkCheck,
                        label = stringResource(R.string.server_settings_metadata_test),
                        loading = extras.testing,
                        clickable = !extras.testing,
                        enabled = enabled,
                        destination = ListItemDestination.Action,
                        onClick = onTest,
                    ),
                ),
        )
        val choices = MetadataProvider.entries.map { it to stringResource(it.labelRes()) }
        ItemGroup(
            title = stringResource(R.string.server_settings_metadata_selection),
            rows =
                listOf(
                    choiceSettingItem(
                        Icons.Filled.Tv,
                        stringResource(R.string.server_settings_metadata_tv),
                        choices,
                        draft.tv,
                        enabled,
                        choiceIcon = { it.icon() },
                    ) { provider ->
                        actions.onEdit { it.copy(tv = provider) }
                    },
                    choiceSettingItem(
                        Icons.Filled.Animation,
                        stringResource(R.string.server_settings_metadata_anime),
                        choices,
                        draft.anime,
                        enabled,
                        choiceIcon = { it.icon() },
                    ) { provider -> actions.onEdit { it.copy(anime = provider) } },
                ),
        )
    }
}

/** One provider and what its last test said, as a status chip tinted as the web client tints its badge. */
@Composable
private fun statusItem(
    provider: MetadataProvider,
    check: ProviderCheck,
): ListItem =
    ListItem(
        icon = provider.icon(),
        label = stringResource(provider.labelRes()),
        trailingContent = {
            RequestStateChip(
                label =
                    stringResource(
                        when (check) {
                            ProviderCheck.NotTested -> R.string.server_settings_metadata_not_tested
                            ProviderCheck.Operational -> R.string.server_settings_metadata_operational
                            ProviderCheck.Failed -> R.string.server_settings_metadata_failed
                        },
                    ),
                tone =
                    when (check) {
                        ProviderCheck.NotTested -> RequestStateTone.Blocked
                        ProviderCheck.Operational -> RequestStateTone.Success
                        ProviderCheck.Failed -> RequestStateTone.Declined
                    },
            )
        },
        clickable = false,
    )

private fun MetadataProvider.labelRes(): Int =
    when (this) {
        MetadataProvider.Tmdb -> R.string.server_settings_metadata_tmdb
        MetadataProvider.Tvdb -> R.string.server_settings_metadata_tvdb
    }

/** TMDB by the films its name is for, TVDB by its television: the two read apart at a glance on both the status and the picks. */
private fun MetadataProvider.icon(): ImageVector =
    when (this) {
        MetadataProvider.Tmdb -> Icons.Filled.Movie
        MetadataProvider.Tvdb -> Icons.Filled.Tv
    }
