package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.DisplayLanguages
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerList
import io.github.scottcooper92.binge.seerr.ui.settings.server.displayLanguageSettingItem
import io.github.scottcooper92.binge.seerr.ui.settings.server.languageSettingItem
import io.github.scottcooper92.binge.seerr.ui.settings.server.limitRows
import io.github.scottcooper92.binge.seerr.ui.settings.server.regionSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.labelRes
import kotlinx.coroutines.flow.Flow

/**
 * A user's General settings as the web client's page lists them, in groups of list rows: who the account is, what
 * Discover shows them, and, for a manager, their request quotas, then the Plex watchlist's auto-requests where the
 * server has them. A text value is edited in a sheet that checks it, and a pick from the server's lists in a picker
 * sheet whose first choice is the server's own setting. [onLoadList] reads one of those lists the first time its picker
 * opens.
 */
@Composable
fun GeneralSettingsScreen(
    state: ExtrasEditorUiState<GeneralSettings, UserGeneralExtras>,
    events: Flow<EditorEvent>,
    actions: EditorActions<GeneralSettings>,
    onLoadList: (ServerList) -> Unit = {},
) {
    val extras = (state as? ExtrasEditorUiState.Ready<GeneralSettings, UserGeneralExtras>)?.extras ?: UserGeneralExtras()
    EditorPage(
        title = stringResource(R.string.user_settings_page_general),
        state = state.toEditorUiState(),
        events = events,
        actions = actions,
        canSave = { it.valid },
        saveAsMade = true,
    ) { draft, enabled ->
        ProfileGroup(draft, extras, enabled, actions)
        DiscoverGroup(draft, extras, enabled, onLoadList, actions)
        if (draft.canEditQuotas) {
            QuotasGroup(draft, enabled, actions)
        }
        WatchlistGroup(draft, enabled, actions)
    }
}

@Composable
private fun ProfileGroup(
    draft: GeneralSettings,
    extras: UserGeneralExtras,
    enabled: Boolean,
    actions: EditorActions<GeneralSettings>,
) {
    val emailError = stringResource(R.string.user_settings_email_invalid)
    val serverLanguage = DisplayLanguages.nativeName(extras.serverDefaults.locale.ifBlank { DEFAULT_LOCALE })
    ItemGroup(
        title = stringResource(R.string.user_settings_section_profile),
        rows =
            listOfNotNull(
                draft.accountType?.let { origin ->
                    readOut(Icons.Filled.Badge, stringResource(R.string.user_settings_account_type), stringResource(origin.labelRes()))
                },
                readOut(
                    Icons.Filled.AdminPanelSettings,
                    stringResource(R.string.user_settings_role),
                    stringResource(draft.role.labelRes()),
                ),
                textSettingItem(
                    icon = Icons.Filled.Person,
                    label = stringResource(R.string.user_settings_display_name),
                    value = draft.displayName,
                    enabled = enabled,
                    onChange = { value -> actions.onEdit { it.copy(displayName = value) } },
                    // What the server itself shows when this is blank, so the row answers "or what?".
                    emptyLabel = draft.fallbackName.ifBlank { stringResource(R.string.settings_value_not_set) },
                    placeholder = draft.fallbackName.takeIf { it.isNotBlank() },
                ),
                textSettingItem(
                    icon = Icons.Filled.Email,
                    label = stringResource(R.string.user_settings_email),
                    value = draft.email,
                    enabled = enabled && draft.canEditEmail,
                    onChange = { value -> actions.onEdit { it.copy(email = value) } },
                    placeholder = stringResource(R.string.placeholder_email),
                    required = draft.emailRequired,
                    check = { value -> emailError.takeIf { !draft.copy(email = value).emailValid } },
                    keyboard = EmailKeyboard,
                ),
                displayLanguageSettingItem(
                    icon = Icons.Filled.Translate,
                    title = stringResource(R.string.settings_display_language),
                    choices =
                        listOf("" to stringResource(R.string.settings_value_server_default, serverLanguage)) +
                            DisplayLanguages.choices(extras.variant, draft.locale),
                    selected = draft.locale.trim(),
                    enabled = enabled,
                    onSelect = { code -> actions.onEdit { it.copy(locale = code) } },
                ),
            ),
    )
}

@Composable
private fun DiscoverGroup(
    draft: GeneralSettings,
    extras: UserGeneralExtras,
    enabled: Boolean,
    onLoadList: (ServerList) -> Unit,
    actions: EditorActions<GeneralSettings>,
) {
    val defaults = extras.serverDefaults
    ItemGroup(
        title = stringResource(R.string.user_settings_section_discover),
        rows =
            listOfNotNull(
                regionSettingItem(
                    icon = Icons.Filled.Public,
                    title = stringResource(R.string.server_settings_discover_region),
                    value = draft.region,
                    choices = extras.lists[ServerList.DiscoverRegions],
                    enabled = enabled,
                    onOpen = { onLoadList(ServerList.DiscoverRegions) },
                    onSelect = { code -> actions.onEdit { it.copy(region = code) } },
                    serverDefault = defaults.region,
                ),
                languageSettingItem(
                    icon = Icons.Filled.Language,
                    title = stringResource(R.string.server_settings_discover_language),
                    value = draft.originalLanguage,
                    choices = extras.lists[ServerList.Languages],
                    enabled = enabled,
                    onOpen = { onLoadList(ServerList.Languages) },
                    onSelect = { codes -> actions.onEdit { it.copy(originalLanguage = codes) } },
                    serverDefault = defaults.originalLanguage,
                ),
                draft.streamingRegion?.let { region ->
                    regionSettingItem(
                        icon = Icons.Filled.LiveTv,
                        title = stringResource(R.string.server_settings_streaming_region),
                        value = region,
                        choices = extras.lists[ServerList.StreamingRegions],
                        enabled = enabled,
                        onOpen = { onLoadList(ServerList.StreamingRegions) },
                        onSelect = { code -> actions.onEdit { it.copy(streamingRegion = code) } },
                        serverDefault = defaults.streamingRegion,
                    )
                },
            ),
    )
}

/** Each quota is the server's until its override is on; then its limit, and its window while limited, hang beneath. */
@Composable
private fun QuotasGroup(
    draft: GeneralSettings,
    enabled: Boolean,
    actions: EditorActions<GeneralSettings>,
) {
    ItemGroup(
        title = stringResource(R.string.user_settings_quotas),
        rows =
            quotaRows(
                QuotaRow(Icons.Filled.Movie, R.string.user_settings_quota_override_movie, R.string.server_settings_movie_limit),
                draft.movieQuotaOverride,
                draft.movieQuotaLimit,
                draft.movieQuotaDays,
                draft.defaultMovieQuota,
                enabled,
                onOverride = { on -> actions.onEdit { it.copy(movieQuotaOverride = on) } },
                onChange = { limit, days -> actions.onEdit { it.copy(movieQuotaLimit = limit, movieQuotaDays = days) } },
            ) +
                quotaRows(
                    QuotaRow(Icons.Filled.Tv, R.string.user_settings_quota_override_tv, R.string.server_settings_tv_limit),
                    draft.tvQuotaOverride,
                    draft.tvQuotaLimit,
                    draft.tvQuotaDays,
                    draft.defaultTvQuota,
                    enabled,
                    onOverride = { on -> actions.onEdit { it.copy(tvQuotaOverride = on) } },
                    onChange = { limit, days -> actions.onEdit { it.copy(tvQuotaLimit = limit, tvQuotaDays = days) } },
                ),
    )
}

/** One media type's quota rows: the override's icon, its switch's label and its limit's label. */
private class QuotaRow(
    val icon: ImageVector,
    val overrideLabel: Int,
    val limitLabel: Int,
)

@Composable
private fun quotaRows(
    row: QuotaRow,
    override: Boolean,
    limit: Int,
    days: Int,
    default: QuotaDefault?,
    enabled: Boolean,
    onOverride: (Boolean) -> Unit,
    onChange: (limit: Int, days: Int) -> Unit,
): List<ListItem> {
    val toggle =
        editorToggle(
            row.icon,
            stringResource(row.overrideLabel),
            override,
            enabled,
            // Off, the server's limit is what applies, so the row says what that is.
            detail = if (override) null else stringResource(R.string.user_settings_quota_default, default.label()),
            onToggle = onOverride,
        )
    return if (override) {
        listOf(toggle) + limitRows(row.icon, stringResource(row.limitLabel), limit, days, enabled, onChange).joined()
    } else {
        listOf(toggle)
    }
}

@Composable
private fun WatchlistGroup(
    draft: GeneralSettings,
    enabled: Boolean,
    actions: EditorActions<GeneralSettings>,
) {
    val rows =
        listOfNotNull(
            draft.watchlistSyncMovies?.let { on ->
                editorToggle(Icons.Filled.Bookmark, stringResource(R.string.user_settings_watchlist_movies), on, enabled) { value ->
                    actions.onEdit { it.copy(watchlistSyncMovies = value) }
                }
            },
            draft.watchlistSyncTv?.let { on ->
                editorToggle(Icons.Filled.Bookmark, stringResource(R.string.user_settings_watchlist_tv), on, enabled) { value ->
                    actions.onEdit { it.copy(watchlistSyncTv = value) }
                }
            },
        )
    if (rows.isNotEmpty()) ItemGroup(title = null, rows = rows)
}

/** A fact about the account the page shows and does not edit. */
private fun readOut(
    icon: ImageVector,
    label: String,
    value: String,
) = ListItem(icon = icon, label = label, detail = value, clickable = false)

private fun UserRole.labelRes(): Int =
    when (this) {
        UserRole.Owner -> R.string.user_role_owner
        UserRole.Admin -> R.string.hub_role_admin
        UserRole.User -> R.string.hub_role_user
    }

@Composable
private fun QuotaDefault?.label(): String =
    if (this == null || limit <= 0) {
        stringResource(R.string.settings_request_limit_unlimited)
    } else {
        stringResource(R.string.settings_request_limit_value, limit, days)
    }

/** The language a server with no display language of its own shows its pages in. */
private const val DEFAULT_LOCALE = "en"
