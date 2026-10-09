package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.DisplayLanguages
import io.github.scottcooper92.binge.seerr.ui.users.settings.AddressKeyboard
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.GroupMessage
import io.github.scottcooper92.binge.seerr.ui.users.settings.choiceSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle
import io.github.scottcooper92.binge.seerr.ui.users.settings.textSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.toEditorUiState
import kotlinx.coroutines.flow.Flow

/**
 * The server's General settings as the web client's General page lists them, in four groups of list rows: the
 * application, what Discover shows, how series are requested, and what the web client marks advanced. A text value is
 * edited in a sheet that checks it, and a pick from the server's lists in a picker sheet, so the page holds no field
 * that can be wrong. [onLoadList] reads one of those lists the first time its picker opens. On a server with an automatic
 * blocklist, a Blocklist group follows Discover; [keywordActions] names its tags and opens the page that edits them.
 */
@Composable
fun ServerGeneralScreen(
    state: ExtrasEditorUiState<ServerGeneralSettings, ServerGeneralExtras>,
    events: Flow<EditorEvent>,
    actions: EditorActions<ServerGeneralSettings>,
    keyActions: ApiKeyActions,
    onLoadList: (ServerList) -> Unit = {},
    keywordActions: KeywordActions = KeywordActions(onLoadNames = {}, onOpen = {}),
) {
    val extras = (state as? ExtrasEditorUiState.Ready<ServerGeneralSettings, ServerGeneralExtras>)?.extras ?: ServerGeneralExtras()
    EditorPage(
        title = stringResource(R.string.server_settings_general_title),
        state = state.toEditorUiState(),
        events = events,
        actions = actions,
        canSave = { it.valid },
    ) { draft, enabled ->
        ApplicationGroup(draft, extras, keyActions, enabled, actions)
        DiscoverGroup(draft, extras, enabled, onLoadList, actions)
        draft.blocklist?.let { blocklist -> BlocklistGroup(blocklist, extras, enabled, onLoadList, keywordActions, actions) }
        RequestsGroup(draft, enabled, actions)
        AdvancedGroup(draft, enabled, actions)
    }
}

@Composable
private fun ApplicationGroup(
    draft: ServerGeneralSettings,
    extras: ServerGeneralExtras,
    keyActions: ApiKeyActions,
    enabled: Boolean,
    actions: EditorActions<ServerGeneralSettings>,
) {
    val urlError = stringResource(R.string.editor_error_web_url)
    ItemGroup(
        title = stringResource(R.string.server_settings_section_application),
        rows =
            listOf(
                textSettingItem(
                    icon = Icons.Filled.Title,
                    label = stringResource(R.string.server_settings_application_title),
                    value = draft.applicationTitle,
                    enabled = enabled,
                    onChange = { value -> actions.onEdit { it.copy(applicationTitle = value) } },
                ),
                textSettingItem(
                    icon = Icons.Filled.Link,
                    label = stringResource(R.string.settings_application_url),
                    value = draft.applicationUrl,
                    enabled = enabled,
                    onChange = { value -> actions.onEdit { it.copy(applicationUrl = value) } },
                    keyboard = AddressKeyboard,
                    hint = stringResource(R.string.server_settings_application_url_hint),
                    check = { value -> urlError.takeIf { !draft.copy(applicationUrl = value).urlValid } },
                ),
                choiceSettingItem(
                    icon = Icons.Filled.Translate,
                    title = stringResource(R.string.settings_display_language),
                    choices = DisplayLanguages.choices(extras.variant, draft.locale),
                    selected = draft.locale,
                    enabled = enabled,
                    onSelect = { code -> actions.onEdit { it.copy(locale = code) } },
                ),
                apiKeyItem(extras.apiKey, keyActions, enabled),
            ),
    )
}

@Composable
private fun DiscoverGroup(
    draft: ServerGeneralSettings,
    extras: ServerGeneralExtras,
    enabled: Boolean,
    onLoadList: (ServerList) -> Unit,
    actions: EditorActions<ServerGeneralSettings>,
) {
    ItemGroup(
        title = stringResource(R.string.user_settings_section_discover),
        rows =
            listOfNotNull(
                regionSettingItem(
                    icon = Icons.Filled.Public,
                    title = stringResource(R.string.server_settings_discover_region),
                    value = draft.discoverRegion,
                    choices = extras.lists[ServerList.DiscoverRegions],
                    enabled = enabled,
                    onOpen = { onLoadList(ServerList.DiscoverRegions) },
                    onSelect = { code -> actions.onEdit { it.copy(discoverRegion = code) } },
                ),
                // Only the Jellyseerr lineage has a streaming region, and only it is asked for the list.
                draft.streamingRegion?.let { region ->
                    regionSettingItem(
                        icon = Icons.Filled.LiveTv,
                        title = stringResource(R.string.server_settings_streaming_region),
                        value = region,
                        choices = extras.lists[ServerList.StreamingRegions],
                        enabled = enabled,
                        onOpen = { onLoadList(ServerList.StreamingRegions) },
                        onSelect = { code -> actions.onEdit { it.copy(streamingRegion = code) } },
                    )
                },
                languageSettingItem(
                    icon = Icons.Filled.Language,
                    title = stringResource(R.string.server_settings_discover_language),
                    value = draft.originalLanguage,
                    choices = extras.lists[ServerList.Languages],
                    enabled = enabled,
                    onOpen = { onLoadList(ServerList.Languages) },
                    onSelect = { codes -> actions.onEdit { it.copy(originalLanguage = codes) } },
                ),
                editorToggle(
                    Icons.Filled.Visibility,
                    stringResource(R.string.settings_hide_available),
                    draft.hideAvailable,
                    enabled,
                    detail = stringResource(R.string.server_settings_hide_available_detail),
                ) { on -> actions.onEdit { it.copy(hideAvailable = on) } },
                draft.hideBlocklisted?.let { hidden ->
                    editorToggle(
                        Icons.Filled.Block,
                        stringResource(R.string.server_settings_hide_blocklisted),
                        hidden,
                        enabled,
                        detail = stringResource(R.string.server_settings_hide_blocklisted_detail),
                    ) { on -> actions.onEdit { it.copy(hideBlocklisted = on) } }
                },
                draft.hideRequested?.let { hidden ->
                    editorToggle(
                        Icons.Filled.VisibilityOff,
                        stringResource(R.string.server_settings_hide_requested),
                        hidden,
                        enabled,
                        detail = stringResource(R.string.server_settings_hide_requested_detail),
                    ) { on -> actions.onEdit { it.copy(hideRequested = on) } }
                },
            ),
    )
}

@Composable
internal fun RequestsGroup(
    draft: ServerGeneralSettings,
    enabled: Boolean,
    actions: EditorActions<ServerGeneralSettings>,
) {
    ItemGroup(
        title = stringResource(R.string.hub_section_requests),
        rows =
            listOfNotNull(
                editorToggle(
                    Icons.Filled.Layers,
                    stringResource(R.string.server_settings_partial_requests),
                    draft.partialRequests,
                    enabled,
                    detail = stringResource(R.string.server_settings_partial_requests_detail),
                ) { on -> actions.onEdit { it.copy(partialRequests = on) } },
                draft.specialEpisodes?.let { allowed ->
                    editorToggle(
                        Icons.Filled.Event,
                        stringResource(R.string.server_settings_special_episodes),
                        allowed,
                        enabled,
                        detail = stringResource(R.string.server_settings_special_episodes_detail),
                    ) { on -> actions.onEdit { it.copy(specialEpisodes = on) } }
                },
            ),
    )
}

/** What the web client badges advanced or experimental. Proxy and CSRF are here on Overseerr only; the forks moved them to Network. */
@Composable
internal fun AdvancedGroup(
    draft: ServerGeneralSettings,
    enabled: Boolean,
    actions: EditorActions<ServerGeneralSettings>,
) {
    ItemGroup(
        title = stringResource(R.string.server_settings_section_advanced),
        rows =
            listOfNotNull(
                editorToggle(
                    Icons.Filled.Image,
                    stringResource(R.string.server_settings_cache_images),
                    draft.cacheImages,
                    enabled,
                    detail = stringResource(R.string.server_settings_cache_images_detail),
                ) { on -> actions.onEdit { it.copy(cacheImages = on) } },
                draft.youtubeUrl?.let { url ->
                    textSettingItem(
                        icon = Icons.Filled.PlayCircle,
                        label = stringResource(R.string.server_settings_youtube_url),
                        value = url,
                        enabled = enabled,
                        onChange = { value -> actions.onEdit { it.copy(youtubeUrl = value) } },
                        keyboard = AddressKeyboard,
                        emptyLabel = stringResource(R.string.server_settings_youtube_default),
                        hint = stringResource(R.string.server_settings_youtube_url_hint),
                    )
                },
                draft.versionCheck?.let { checking ->
                    editorToggle(Icons.Filled.Update, stringResource(R.string.server_settings_version_check), checking, enabled) { on ->
                        actions.onEdit { it.copy(versionCheck = on) }
                    }
                },
                draft.trustProxy?.let { trusted ->
                    editorToggle(
                        Icons.Filled.Shield,
                        stringResource(R.string.server_settings_trust_proxy),
                        trusted,
                        enabled,
                        detail = stringResource(R.string.server_settings_trust_proxy_detail),
                    ) { on -> actions.onEdit { it.copy(trustProxy = on) } }
                },
                draft.csrfProtection?.let { protected ->
                    editorToggle(
                        Icons.Filled.Lock,
                        stringResource(R.string.server_settings_csrf),
                        protected,
                        enabled,
                        detail = stringResource(R.string.server_settings_csrf_detail),
                    ) { on -> actions.onEdit { it.copy(csrfProtection = on) } }
                },
            ),
        // Overseerr badges both "Restart required", as the forks do on their Network page (#868).
        belowRows =
            if (draft.trustProxy != null || draft.csrfProtection != null) {
                { GroupMessage(stringResource(R.string.server_settings_restart_proxy_csrf), error = false) }
            } else {
                null
            },
    )
}
