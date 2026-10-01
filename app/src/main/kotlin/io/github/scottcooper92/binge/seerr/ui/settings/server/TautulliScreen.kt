package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Tag
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSectionCard
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorToggleRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle
import kotlinx.coroutines.flow.Flow

/** The Tautulli page: where it is, how to reach it, and the key it answers to. */
@Composable
fun TautulliScreen(
    state: EditorUiState<TautulliForm>,
    events: Flow<EditorEvent>,
    actions: EditorActions<TautulliForm>,
) {
    EditorPage(
        title = stringResource(R.string.server_settings_tautulli),
        state = state,
        events = events,
        actions = actions,
        canSave = { it.valid },
    ) { draft, enabled ->
        EditorSectionCard(stringResource(R.string.settings_group_connection)) {
            EditorTextField(
                draft.host,
                stringResource(R.string.server_settings_host),
                icon = Icons.Filled.Dns,
                enabled = enabled,
                keyboardType = KeyboardType.Uri,
                placeholder = stringResource(R.string.placeholder_host),
            ) { value ->
                actions.onEdit { it.copy(host = value) }
            }
            EditorTextField(
                draft.port,
                stringResource(R.string.server_settings_port),
                icon = Icons.Filled.Tag,
                enabled = enabled,
                keyboardType = KeyboardType.Number,
                placeholder = stringResource(R.string.placeholder_port_tautulli),
                isError = draft.port.isNotBlank() && !draft.copy(host = "x", apiKey = "x").valid,
            ) { value -> actions.onEdit { it.copy(port = value) } }
            EditorToggleRow(
                editorToggle(Icons.Filled.Https, stringResource(R.string.server_settings_use_ssl), draft.useSsl, enabled) { value ->
                    actions.onEdit { it.copy(useSsl = value) }
                },
            )
            EditorTextField(
                draft.urlBase,
                stringResource(R.string.server_settings_url_base),
                icon = Icons.AutoMirrored.Filled.AltRoute,
                enabled = enabled,
                placeholder = stringResource(R.string.placeholder_url_base_tautulli),
            ) { value ->
                actions.onEdit { it.copy(urlBase = value) }
            }
            EditorTextField(
                draft.apiKey,
                stringResource(R.string.server_settings_api_key),
                icon = Icons.Filled.Key,
                enabled = enabled,
                secret = true,
            ) { value ->
                actions.onEdit { it.copy(apiKey = value) }
            }
            EditorTextField(
                draft.externalUrl,
                stringResource(R.string.server_settings_external_url),
                icon = Icons.Filled.Link,
                enabled = enabled,
                keyboardType = KeyboardType.Uri,
                supporting = stringResource(R.string.server_settings_tautulli_external_hint),
            ) { value -> actions.onEdit { it.copy(externalUrl = value) } }
        }
    }
}
