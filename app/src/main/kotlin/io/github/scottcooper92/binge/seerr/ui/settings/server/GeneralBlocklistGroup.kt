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
import io.github.scottcooper92.binge.seerr.ui.users.settings.textSettingItem

/** What the tags picker asks of the page: the saved tags' names, and TMDB's keywords for what is typed. */
class KeywordActions(
    val onLoadNames: (List<Int>) -> Unit,
    val onSearch: (String) -> Unit,
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
            listOf(
                regionSettingItem(
                    icon = Icons.Filled.Public,
                    title = stringResource(R.string.server_settings_blocklist_region),
                    value = blocklist.region,
                    choices = extras.lists[ServerList.DiscoverRegions],
                    enabled = enabled,
                    onOpen = { onLoadList(ServerList.DiscoverRegions) },
                    onSelect = { code -> edit { it.copy(region = code) } },
                ),
                languageSettingItem(
                    icon = Icons.Filled.Language,
                    title = stringResource(R.string.server_settings_blocklist_language),
                    value = blocklist.languages,
                    choices = extras.lists[ServerList.Languages],
                    enabled = enabled,
                    onOpen = { onLoadList(ServerList.Languages) },
                    onSelect = { codes -> edit { it.copy(languages = codes) } },
                ),
                keywordSettingItem(
                    icon = Icons.Filled.Sell,
                    title = stringResource(R.string.server_settings_blocklist_tags),
                    chosen = blocklist.tagIds,
                    search = extras.keywords,
                    enabled = enabled,
                    onOpen = { keywordActions.onLoadNames(blocklist.tagIds) },
                    onQuery = keywordActions.onSearch,
                ) { id ->
                    edit { it.copy(tags = it.tagIds.toggledIn(id).joinToString(",")) }
                },
                textSettingItem(
                    icon = Icons.Filled.Numbers,
                    label = stringResource(R.string.server_settings_blocklist_limit),
                    value = blocklist.tagsLimit,
                    enabled = enabled,
                    onChange = { value -> edit { it.copy(tagsLimit = value) } },
                    hint = stringResource(R.string.server_settings_blocklist_limit_hint),
                    required = true,
                    check = { value -> limitError.takeIf { !blocklist.copy(tagsLimit = value).tagsLimitValid } },
                ),
            ),
    )
}

/** [id] added at the end if it is not there, else taken out, keeping the order the server holds the rest in. */
internal fun List<Int>.toggledIn(id: Int): List<Int> = if (id in this) this - id else this + id
