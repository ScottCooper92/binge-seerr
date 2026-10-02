package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTag
import com.binge.designsystem.component.BingeTextButton
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.labelRes
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSectionCard
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorToggleGroup
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorToggleRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle
import io.github.scottcooper92.binge.seerr.ui.users.settings.toEditorUiState
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
 * way into the default permissions and the API key behind a reveal.
 */
@Composable
fun ServerGeneralScreen(
    state: ExtrasEditorUiState<ServerGeneralSettings, ServerGeneralExtras>,
    events: Flow<EditorEvent>,
    actions: EditorActions<ServerGeneralSettings>,
    keyActions: ApiKeyActions,
    onOpenDefaultPermissions: () -> Unit,
) {
    val extras =
        (state as? ExtrasEditorUiState.Ready<ServerGeneralSettings, ServerGeneralExtras>)?.extras ?: ServerGeneralExtras()
    EditorPage(
        title = stringResource(R.string.server_settings_general_title),
        state = state.toEditorUiState(),
        events = events,
        actions = actions,
        canSave = { it.valid },
    ) { draft, enabled ->
        GeneralFields(draft, enabled, actions)
        DiscoverFields(draft, enabled, actions)
        RequestSwitches(draft, enabled, actions)
        ServerSwitches(draft, enabled, actions)
        EditorSectionCard(stringResource(R.string.server_settings_section_users)) {
            if (extras.defaultPermissions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
                ) {
                    extras.defaultPermissions.forEach { permission -> BingeTag(label = stringResource(permission.labelRes())) }
                }
            }
            BingeOutlinedButton(
                label = stringResource(R.string.server_settings_default_permissions),
                onClick = onOpenDefaultPermissions,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ApiKeySection(extras.apiKey, keyActions)
    }
}

@Composable
private fun GeneralFields(
    draft: ServerGeneralSettings,
    enabled: Boolean,
    actions: EditorActions<ServerGeneralSettings>,
) {
    EditorSectionCard(stringResource(R.string.server_settings_section_application)) {
        EditorTextField(
            draft.applicationTitle,
            stringResource(R.string.server_settings_application_title),
            icon = Icons.Filled.Title,
            enabled = enabled,
            prose = true,
        ) { value ->
            actions.onEdit { it.copy(applicationTitle = value) }
        }
        EditorTextField(
            draft.applicationUrl,
            stringResource(R.string.settings_application_url),
            icon = Icons.Filled.Link,
            enabled = enabled,
            keyboardType = KeyboardType.Uri,
            supporting = stringResource(R.string.server_settings_application_url_hint),
            isError = !draft.urlValid,
        ) { value -> actions.onEdit { it.copy(applicationUrl = value) } }
    }
}

@Composable
private fun DiscoverFields(
    draft: ServerGeneralSettings,
    enabled: Boolean,
    actions: EditorActions<ServerGeneralSettings>,
) {
    EditorSectionCard(stringResource(R.string.user_settings_section_discover)) {
        EditorTextField(
            draft.locale,
            stringResource(R.string.settings_display_language),
            icon = Icons.Filled.Translate,
            enabled = enabled,
            supporting = stringResource(R.string.server_settings_locale_hint),
            isError = !draft.localeValid,
        ) { value -> actions.onEdit { it.copy(locale = value) } }
        EditorTextField(
            draft.discoverRegion,
            stringResource(R.string.server_settings_discover_region),
            icon = Icons.Filled.Public,
            enabled = enabled,
            supporting = stringResource(R.string.server_settings_region_hint),
            isError = !draft.discoverRegionValid,
        ) { value -> actions.onEdit { it.copy(discoverRegion = value) } }
        draft.streamingRegion?.let { region ->
            EditorTextField(
                region,
                stringResource(R.string.server_settings_streaming_region),
                icon = Icons.Filled.LiveTv,
                enabled = enabled,
                supporting = stringResource(R.string.server_settings_region_hint),
                isError = !draft.streamingRegionValid,
            ) { value -> actions.onEdit { it.copy(streamingRegion = value) } }
        }
        EditorTextField(
            draft.originalLanguage,
            stringResource(R.string.user_settings_original_language),
            icon = Icons.Filled.Language,
            enabled = enabled,
            supporting = stringResource(R.string.server_settings_original_language_hint),
            isError = !draft.originalLanguageValid,
        ) { value -> actions.onEdit { it.copy(originalLanguage = value) } }
    }
}

@Composable
private fun RequestSwitches(
    draft: ServerGeneralSettings,
    enabled: Boolean,
    actions: EditorActions<ServerGeneralSettings>,
) {
    EditorToggleGroup(
        stringResource(R.string.settings_group_requests),
        listOfNotNull(
            editorToggle(Icons.Filled.Visibility, stringResource(R.string.settings_hide_available), draft.hideAvailable, enabled) { value ->
                actions.onEdit { it.copy(hideAvailable = value) }
            },
            draft.hideRequested?.let { on ->
                editorToggle(Icons.Filled.VisibilityOff, stringResource(R.string.server_settings_hide_requested), on, enabled) { value ->
                    actions.onEdit { it.copy(hideRequested = value) }
                }
            },
            editorToggle(
                Icons.Filled.Layers,
                stringResource(R.string.server_settings_partial_requests),
                draft.partialRequests,
                enabled,
            ) { value ->
                actions.onEdit { it.copy(partialRequests = value) }
            },
            draft.specialEpisodes?.let { on ->
                editorToggle(Icons.Filled.Event, stringResource(R.string.server_settings_special_episodes), on, enabled) { value ->
                    actions.onEdit { it.copy(specialEpisodes = value) }
                }
            },
        ),
    )
}

@Composable
internal fun ServerSwitches(
    draft: ServerGeneralSettings,
    enabled: Boolean,
    actions: EditorActions<ServerGeneralSettings>,
) {
    EditorSectionCard(stringResource(R.string.settings_server)) {
        draft.versionCheck?.let { on ->
            EditorToggleRow(
                editorToggle(Icons.Filled.Update, stringResource(R.string.server_settings_version_check), on, enabled) { value ->
                    actions.onEdit { it.copy(versionCheck = value) }
                },
            )
        }
        EditorToggleRow(
            editorToggle(Icons.Filled.Image, stringResource(R.string.server_settings_cache_images), draft.cacheImages, enabled) { value ->
                actions.onEdit { it.copy(cacheImages = value) }
            },
        )
        draft.youtubeUrl?.let { url ->
            EditorTextField(
                url,
                stringResource(R.string.server_settings_youtube_url),
                icon = Icons.Filled.PlayCircle,
                enabled = enabled,
                keyboardType = KeyboardType.Uri,
                supporting = stringResource(R.string.server_settings_youtube_url_hint),
            ) { value -> actions.onEdit { it.copy(youtubeUrl = value) } }
        }
        draft.trustProxy?.let { on ->
            EditorToggleRow(
                editorToggle(Icons.Filled.Shield, stringResource(R.string.server_settings_trust_proxy), on, enabled) { value ->
                    actions.onEdit { it.copy(trustProxy = value) }
                },
            )
        }
        draft.csrfProtection?.let { on ->
            EditorToggleRow(
                editorToggle(Icons.Filled.Lock, stringResource(R.string.server_settings_csrf), on, enabled) { value ->
                    actions.onEdit { it.copy(csrfProtection = value) }
                },
            )
        }
    }
}

/** Masked until revealed; regenerating asks first, since the old key stops working at once. */
@Composable
internal fun ApiKeySection(
    apiKey: ApiKeyState,
    actions: ApiKeyActions,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    EditorSectionCard(stringResource(R.string.server_settings_api_key)) {
        EditorTextField(
            apiKey.key,
            stringResource(R.string.server_settings_api_key),
            icon = Icons.Filled.Key,
            secret = true,
            readOnly = true,
            revealed = apiKey.revealed,
            onToggleReveal = actions.onToggleReveal,
        ) {}
        Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)), modifier = Modifier.fillMaxWidth()) {
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
                destructive = true,
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
}
