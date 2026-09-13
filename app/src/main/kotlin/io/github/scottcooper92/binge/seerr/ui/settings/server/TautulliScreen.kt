package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSwitchRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
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
        EditorTextField(
            draft.host,
            stringResource(R.string.server_settings_host),
            enabled = enabled,
            keyboardType = KeyboardType.Uri,
        ) { value ->
            actions.onEdit { it.copy(host = value) }
        }
        EditorTextField(
            draft.port,
            stringResource(R.string.server_settings_port),
            enabled = enabled,
            keyboardType = KeyboardType.Number,
            isError = draft.port.isNotBlank() && !draft.copy(host = "x", apiKey = "x").valid,
        ) { value -> actions.onEdit { it.copy(port = value) } }
        EditorSwitchRow(stringResource(R.string.server_settings_use_ssl), draft.useSsl, enabled = enabled) { value ->
            actions.onEdit { it.copy(useSsl = value) }
        }
        EditorTextField(draft.urlBase, stringResource(R.string.server_settings_url_base), enabled = enabled) { value ->
            actions.onEdit { it.copy(urlBase = value) }
        }
        EditorTextField(draft.apiKey, stringResource(R.string.server_settings_api_key), enabled = enabled, secret = true) { value ->
            actions.onEdit { it.copy(apiKey = value) }
        }
        EditorTextField(
            draft.externalUrl,
            stringResource(R.string.server_settings_external_url),
            enabled = enabled,
            keyboardType = KeyboardType.Uri,
            supporting = stringResource(R.string.server_settings_tautulli_external_hint),
        ) { value -> actions.onEdit { it.copy(externalUrl = value) } }
    }
}
