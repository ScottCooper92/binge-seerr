package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemConnector
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.labelRes
import io.github.scottcooper92.binge.seerr.ui.users.labelRes
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.GroupMessage
import io.github.scottcooper92.binge.seerr.ui.users.settings.choiceSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle
import io.github.scottcooper92.binge.seerr.ui.users.settings.toEditorUiState
import kotlinx.coroutines.flow.Flow

/**
 * The web client's Users settings page, as list rows: how people sign in, the request limits everyone gets, and the
 * way into the permissions a new user starts with. A limit is picked as the web client picks it, from 1 to 100 over
 * 1 to 100 days, and its window hangs off it only while there is a limit.
 */
@Composable
fun ServerUsersScreen(
    state: ExtrasEditorUiState<ServerUsersSettings, ServerUsersExtras>,
    events: Flow<EditorEvent>,
    actions: EditorActions<ServerUsersSettings>,
    onOpenDefaultPermissions: () -> Unit,
) {
    val extras = (state as? ExtrasEditorUiState.Ready<ServerUsersSettings, ServerUsersExtras>)?.extras ?: ServerUsersExtras()
    EditorPage(
        title = stringResource(R.string.hub_section_users),
        state = state.toEditorUiState(),
        events = events,
        actions = actions,
        canSave = { it.valid },
    ) { draft, enabled ->
        SignInGroup(draft, stringResource(extras.mediaServer.labelRes()), enabled, actions)
        ItemGroup(
            title = stringResource(R.string.server_settings_request_limits),
            rows =
                limitRows(
                    Icons.Filled.Movie,
                    stringResource(R.string.server_settings_movie_limit),
                    draft.movieLimit,
                    draft.movieDays,
                    enabled,
                ) { limit, days -> actions.onEdit { it.copy(movieLimit = limit, movieDays = days) } } +
                    limitRows(
                        Icons.Filled.Tv,
                        stringResource(R.string.server_settings_tv_limit),
                        draft.tvLimit,
                        draft.tvDays,
                        enabled,
                    ) { limit, days -> actions.onEdit { it.copy(tvLimit = limit, tvDays = days) } },
        )
        val permissions = extras.defaultPermissions.map { stringResource(it.labelRes()) }.sorted()
        ItemGroup(
            title = null,
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Filled.Shield,
                        label = stringResource(R.string.server_settings_default_permissions),
                        detail = permissions.joinToString(", ").ifEmpty { stringResource(R.string.server_settings_permissions_none) },
                        clickable = enabled,
                        disabled = !enabled,
                        onClick = onOpenDefaultPermissions,
                    ),
                ),
        )
    }
}

@Composable
private fun SignInGroup(
    draft: ServerUsersSettings,
    server: String,
    enabled: Boolean,
    actions: EditorActions<ServerUsersSettings>,
) {
    val required = stringResource(R.string.server_settings_sign_in_required)
    ItemGroup(
        title = stringResource(R.string.server_settings_sign_in),
        rows =
            listOfNotNull(
                editorToggle(
                    Icons.AutoMirrored.Filled.Login,
                    stringResource(R.string.server_settings_local_login),
                    draft.localLogin,
                    enabled,
                    detail = stringResource(R.string.server_settings_local_login_detail),
                ) { on -> actions.onEdit { it.copy(localLogin = on) } },
                draft.mediaServerLogin?.let { allowed ->
                    editorToggle(
                        Icons.Filled.Storage,
                        stringResource(R.string.server_settings_media_server_login, server),
                        allowed,
                        enabled,
                    ) { on -> actions.onEdit { it.copy(mediaServerLogin = on) } }
                },
                editorToggle(
                    Icons.Filled.PersonAdd,
                    stringResource(R.string.server_settings_new_media_server_login, server),
                    draft.newMediaServerLogin,
                    enabled,
                    detail = stringResource(R.string.server_settings_new_media_server_login_detail, server),
                ) { on -> actions.onEdit { it.copy(newMediaServerLogin = on) } },
            ),
        belowRows = if (draft.valid) null else ({ GroupMessage(required, error = true) }),
    )
}

/** A limit as a picker row, and while it is set, its window as a picker row joined beneath it. A user's quota uses it too. */
@Composable
internal fun limitRows(
    icon: ImageVector,
    label: String,
    limit: Int,
    days: Int,
    enabled: Boolean,
    onChange: (limit: Int, days: Int) -> Unit,
): List<ListItem> {
    val unlimited = stringResource(R.string.hub_quota_unlimited)
    val counts =
        listOf(0 to unlimited) + (1..MAX_LIMIT_CHOICE).map { it to pluralStringResource(R.plurals.server_settings_limit_count, it, it) }
    val windows = (1..MAX_LIMIT_CHOICE).map { it to pluralStringResource(R.plurals.hub_quota_period, it, it) }
    val limitRow = choiceSettingItem(icon, label, counts, limit, enabled) { onChange(it, days) }
    val windowRow =
        choiceSettingItem(Icons.Filled.DateRange, stringResource(R.string.server_settings_limit_window), windows, days, enabled) {
            onChange(limit, it)
        }.copy(connector = ListItemConnector.End)
    return if (limit > 0) listOf(limitRow, windowRow) else listOf(limitRow)
}
