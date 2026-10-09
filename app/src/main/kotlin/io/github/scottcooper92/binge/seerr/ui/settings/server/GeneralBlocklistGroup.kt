package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sell
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.NumberKeyboard
import io.github.scottcooper92.binge.seerr.ui.users.settings.textSettingItem

/** What the tags row asks of the page: the saved tags' names, and the way into the blocklisted tags page. */
class KeywordActions(
    val onLoadNames: (List<Int>) -> Unit,
    val onOpen: () -> Unit,
)

/**
 * The automatic blocklist, as the web client's General page has it: the region and languages the "Process Blocklisted
 * Tags" job scans, apart from Discover's, the keywords whose titles it blocklists, and how many pages it takes per tag.
 */
@Composable
internal fun BlocklistGroup(
    blocklist: BlocklistSettings,
    extras: ServerGeneralExtras,
    enabled: Boolean,
    onLoadList: (ServerList) -> Unit,
    keywordActions: KeywordActions,
    actions: EditorActions<ServerGeneralSettings>,
) {
    val edit = { change: (BlocklistSettings) -> BlocklistSettings -> actions.onEdit { it.copy(blocklist = it.blocklist?.let(change)) } }
    val limitError = stringResource(R.string.server_settings_blocklist_limit_error, MAX_TAGS_LIMIT)
    ItemGroup(
        title = stringResource(R.string.server_settings_blocklist),
        rows =
            listOfNotNull(
                blocklist.region?.let { region ->
                    regionSettingItem(
                        icon = Icons.Filled.Public,
                        title = stringResource(R.string.server_settings_blocklist_region),
                        value = region,
                        choices = extras.lists[ServerList.DiscoverRegions],
                        enabled = enabled,
                        onOpen = { onLoadList(ServerList.DiscoverRegions) },
                        onSelect = { code -> edit { it.copy(region = code) } },
                    )
                },
                blocklist.languages?.let { languages ->
                    languageSettingItem(
                        icon = Icons.Filled.Language,
                        title = stringResource(R.string.server_settings_blocklist_language),
                        value = languages,
                        choices = extras.lists[ServerList.Languages],
                        enabled = enabled,
                        onOpen = { onLoadList(ServerList.Languages) },
                        onSelect = { codes -> edit { it.copy(languages = codes) } },
                    )
                },
                keywordSettingItem(
                    icon = Icons.Filled.Sell,
                    title = stringResource(R.string.server_settings_blocklist_tags),
                    chosen = blocklist.tagIds,
                    names = extras.keywords.names,
                    enabled = enabled,
                    onLoadNames = keywordActions.onLoadNames,
                    onOpen = keywordActions.onOpen,
                ),
                textSettingItem(
                    icon = Icons.Filled.Numbers,
                    label = stringResource(R.string.server_settings_blocklist_limit),
                    value = blocklist.tagsLimit,
                    enabled = enabled,
                    onChange = { value -> edit { it.copy(tagsLimit = value) } },
                    keyboard = NumberKeyboard,
                    hint = stringResource(R.string.server_settings_blocklist_limit_hint),
                    required = true,
                    check = { value -> limitError.takeIf { !blocklist.copy(tagsLimit = value).tagsLimitValid } },
                ),
            ),
    )
}

/** [id] added at the end if it is not there, else taken out, keeping the order the server holds the rest in. */
internal fun List<Int>.toggledIn(id: Int): List<Int> = if (id in this) this - id else this + id
