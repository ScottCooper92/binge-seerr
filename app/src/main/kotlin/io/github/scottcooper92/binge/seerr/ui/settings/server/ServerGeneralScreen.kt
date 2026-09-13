package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTextButton
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSectionTitle
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSwitchRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

/** The actions on the API key beside the form: show it, copy it, and replace it. */
class ApiKeyActions(
    val onToggleReveal: () -> Unit,
    val onCopy: (String) -> Unit,
    val onRegenerate: () -> Unit,
)

/**
 * The general page: the main settings form — a field the lineage lacks is simply absent — then the
 * way into the default permissions, the API key behind a reveal, and what a visitor sees.
 */
@Composable
fun ServerGeneralScreen(
    state: EditorUiState<ServerGeneralSettings>,
    extras: ServerGeneralExtras,
    events: Flow<EditorEvent>,
    actions: EditorActions<ServerGeneralSettings>,
    keyActions: ApiKeyActions,
    onOpenDefaultPermissions: () -> Unit,
) {
    EditorPage(
        title = stringResource(R.string.server_settings_general_title),
        state = state,
        events = events,
        actions = actions,
        canSave = { it.urlValid },
    ) { draft, enabled ->
        GeneralFields(draft, enabled, actions)
        DiscoverFields(draft, enabled, actions)
        RequestSwitches(draft, enabled, actions)
        ServerSwitches(draft, enabled, actions)
        EditorSectionTitle(stringResource(R.string.server_settings_section_users))
        BingeOutlinedButton(
            label = stringResource(R.string.server_settings_default_permissions),
            onClick = onOpenDefaultPermissions,
            modifier = Modifier.fillMaxWidth(),
        )
        ApiKeySection(extras.apiKey, keyActions)
        extras.visitor?.let { VisitorSection(it) }
    }
}

@Composable
private fun GeneralFields(
    draft: ServerGeneralSettings,
    enabled: Boolean,
    actions: EditorActions<ServerGeneralSettings>,
) {
    EditorTextField(draft.applicationTitle, stringResource(R.string.server_settings_application_title), enabled = enabled) { value ->
        actions.onEdit { it.copy(applicationTitle = value) }
    }
    EditorTextField(
        draft.applicationUrl,
        stringResource(R.string.settings_application_url),
        enabled = enabled,
        keyboardType = KeyboardType.Uri,
        supporting = stringResource(R.string.server_settings_application_url_hint),
        isError = !draft.urlValid,
    ) { value -> actions.onEdit { it.copy(applicationUrl = value) } }
}

@Composable
private fun DiscoverFields(
    draft: ServerGeneralSettings,
    enabled: Boolean,
    actions: EditorActions<ServerGeneralSettings>,
) {
    EditorSectionTitle(stringResource(R.string.user_settings_section_discover))
    EditorTextField(
        draft.locale,
        stringResource(R.string.settings_display_language),
        enabled = enabled,
        supporting = stringResource(R.string.server_settings_locale_hint),
    ) { value -> actions.onEdit { it.copy(locale = value) } }
    EditorTextField(
        draft.discoverRegion,
        stringResource(R.string.server_settings_discover_region),
        enabled = enabled,
        supporting = stringResource(R.string.server_settings_region_hint),
    ) { value -> actions.onEdit { it.copy(discoverRegion = value) } }
    draft.streamingRegion?.let { region ->
        EditorTextField(
            region,
            stringResource(R.string.server_settings_streaming_region),
            enabled = enabled,
            supporting = stringResource(R.string.server_settings_region_hint),
        ) { value -> actions.onEdit { it.copy(streamingRegion = value) } }
    }
    EditorTextField(
        draft.originalLanguage,
        stringResource(R.string.user_settings_original_language),
        enabled = enabled,
        supporting = stringResource(R.string.server_settings_original_language_hint),
    ) { value -> actions.onEdit { it.copy(originalLanguage = value) } }
}

@Composable
private fun RequestSwitches(
    draft: ServerGeneralSettings,
    enabled: Boolean,
    actions: EditorActions<ServerGeneralSettings>,
) {
    EditorSectionTitle(stringResource(R.string.settings_group_requests))
    EditorSwitchRow(stringResource(R.string.settings_hide_available), draft.hideAvailable, enabled = enabled) { value ->
        actions.onEdit { it.copy(hideAvailable = value) }
    }
    draft.hideRequested?.let { on ->
        EditorSwitchRow(stringResource(R.string.server_settings_hide_requested), on, enabled = enabled) { value ->
            actions.onEdit { it.copy(hideRequested = value) }
        }
    }
    EditorSwitchRow(stringResource(R.string.server_settings_partial_requests), draft.partialRequests, enabled = enabled) { value ->
        actions.onEdit { it.copy(partialRequests = value) }
    }
    draft.specialEpisodes?.let { on ->
        EditorSwitchRow(stringResource(R.string.server_settings_special_episodes), on, enabled = enabled) { value ->
            actions.onEdit { it.copy(specialEpisodes = value) }
        }
    }
}

@Composable
private fun ServerSwitches(
    draft: ServerGeneralSettings,
    enabled: Boolean,
    actions: EditorActions<ServerGeneralSettings>,
) {
    EditorSectionTitle(stringResource(R.string.settings_server))
    draft.versionCheck?.let { on ->
        EditorSwitchRow(stringResource(R.string.server_settings_version_check), on, enabled = enabled) { value ->
            actions.onEdit { it.copy(versionCheck = value) }
        }
    }
    EditorSwitchRow(stringResource(R.string.server_settings_cache_images), draft.cacheImages, enabled = enabled) { value ->
        actions.onEdit { it.copy(cacheImages = value) }
    }
    draft.youtubeUrl?.let { url ->
        EditorTextField(
            url,
            stringResource(R.string.server_settings_youtube_url),
            enabled = enabled,
            keyboardType = KeyboardType.Uri,
            supporting = stringResource(R.string.server_settings_youtube_url_hint),
        ) { value -> actions.onEdit { it.copy(youtubeUrl = value) } }
    }
    draft.trustProxy?.let { on ->
        EditorSwitchRow(stringResource(R.string.server_settings_trust_proxy), on, enabled = enabled) { value ->
            actions.onEdit { it.copy(trustProxy = value) }
        }
    }
    draft.csrfProtection?.let { on ->
        EditorSwitchRow(stringResource(R.string.server_settings_csrf), on, enabled = enabled) { value ->
            actions.onEdit { it.copy(csrfProtection = value) }
        }
    }
}

/** Masked until revealed; regenerating asks first, since the old key stops working at once. */
@Composable
private fun ApiKeySection(
    apiKey: ApiKeyState,
    actions: ApiKeyActions,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    EditorSectionTitle(stringResource(R.string.server_settings_api_key))
    Text(
        text = if (apiKey.revealed) apiKey.key else stringResource(R.string.server_settings_api_key_masked),
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)), modifier = Modifier.fillMaxWidth()) {
        BingeTextButton(
            label = stringResource(if (apiKey.revealed) R.string.server_settings_api_key_hide else R.string.server_settings_api_key_reveal),
            onClick = actions.onToggleReveal,
            enabled = apiKey.key.isNotEmpty(),
        )
        BingeTextButton(
            label = stringResource(R.string.server_settings_api_key_copy),
            onClick = { actions.onCopy(apiKey.key) },
            enabled = apiKey.key.isNotEmpty(),
        )
        BingeTextButton(
            label = stringResource(R.string.server_settings_api_key_regenerate),
            onClick = { confirming = true },
            enabled = !apiKey.regenerating,
            loading = apiKey.regenerating,
        )
    }
    if (confirming) {
        BingeConfirmDialog(
            title = stringResource(R.string.server_settings_api_key_regenerate_title),
            message = stringResource(R.string.server_settings_api_key_regenerate_message),
            confirmLabel = stringResource(R.string.server_settings_api_key_regenerate),
            destructive = true,
            onConfirm = {
                confirming = false
                actions.onRegenerate()
            },
            onDismiss = { confirming = false },
        )
    }
}

/** Read-only: what the server tells a visitor before they sign in, so an admin can check it here. */
@Composable
private fun VisitorSection(visitor: VisitorView) {
    EditorSectionTitle(stringResource(R.string.server_settings_visitor_title))
    Text(
        stringResource(R.string.server_settings_visitor_lead),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    VisitorRow(stringResource(R.string.server_settings_visitor_local_login), visitor.localLogin)
    VisitorRow(stringResource(R.string.server_settings_visitor_media_server_login), visitor.mediaServerLogin)
    VisitorRow(stringResource(R.string.server_settings_visitor_movie_4k), visitor.movie4k)
    VisitorRow(stringResource(R.string.server_settings_visitor_series_4k), visitor.series4k)
    VisitorRow(stringResource(R.string.server_settings_partial_requests), visitor.partialRequests)
    VisitorRow(stringResource(R.string.settings_hide_available), visitor.hideAvailable)
}

@Composable
private fun VisitorRow(
    label: String,
    on: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(if (on) R.string.settings_value_on else R.string.settings_value_off),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
