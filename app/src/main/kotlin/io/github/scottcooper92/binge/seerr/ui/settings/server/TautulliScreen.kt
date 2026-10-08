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
import com.binge.designsystem.component.ItemGroup
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle
import io.github.scottcooper92.binge.seerr.ui.users.settings.textSettingItem
import kotlinx.coroutines.flow.Flow

/**
 * The Tautulli page in the web client's field order, as one group of list rows: where it is, how to reach it, the key
 * it answers to, and the link users are sent to. Each value is checked in the sheet that edits it.
 */
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
        val hostLabel = stringResource(R.string.server_settings_host)
        val keyLabel = stringResource(R.string.server_settings_api_key)
        val portError = stringResource(R.string.editor_error_port)
        val urlError = stringResource(R.string.editor_error_web_url)
        ItemGroup(
            title = stringResource(R.string.settings_group_connection),
            rows =
                listOf(
                    textSettingItem(
                        icon = Icons.Filled.Dns,
                        label = hostLabel,
                        value = draft.host,
                        enabled = enabled,
                        onChange = { value -> actions.onEdit { it.copy(host = value) } },
                        required = draft.configured,
                    ),
                    textSettingItem(
                        icon = Icons.Filled.Tag,
                        label = stringResource(R.string.server_settings_port),
                        value = draft.port,
                        enabled = enabled,
                        onChange = { value -> actions.onEdit { it.copy(port = value) } },
                        required = draft.configured,
                        check = { value -> portError.takeIf { value.isNotBlank() && !portValid(value) } },
                    ),
                    editorToggle(Icons.Filled.Https, stringResource(R.string.server_settings_use_ssl), draft.useSsl, enabled) { on ->
                        actions.onEdit { it.copy(useSsl = on) }
                    },
                    textSettingItem(
                        icon = Icons.AutoMirrored.Filled.AltRoute,
                        label = stringResource(R.string.server_settings_url_base),
                        value = draft.urlBase,
                        enabled = enabled,
                        onChange = { value -> actions.onEdit { it.copy(urlBase = value) } },
                    ),
                    textSettingItem(
                        icon = Icons.Filled.Key,
                        label = keyLabel,
                        value = draft.apiKey,
                        enabled = enabled,
                        onChange = { value -> actions.onEdit { it.copy(apiKey = value) } },
                        required = draft.configured,
                        shown = draft.apiKey.maskedKey() ?: stringResource(R.string.settings_value_not_set),
                        secret = true,
                    ),
                    textSettingItem(
                        icon = Icons.Filled.Link,
                        label = stringResource(R.string.server_settings_external_url),
                        value = draft.externalUrl,
                        enabled = enabled,
                        onChange = { value -> actions.onEdit { it.copy(externalUrl = value) } },
                        hint = stringResource(R.string.server_settings_tautulli_external_hint),
                        check = { value -> urlError.takeIf { !draft.copy(externalUrl = value).externalUrlValid } },
                    ),
                ),
        )
    }
}
