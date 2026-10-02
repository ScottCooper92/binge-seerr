package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.scottcooper92.binge.seerr.R
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

/** The general page: identity and contact, the discovery locale, and, for a manager, the quotas. */
@Composable
fun GeneralSettingsScreen(
    state: EditorUiState<GeneralSettings>,
    events: Flow<EditorEvent>,
    actions: EditorActions<GeneralSettings>,
) {
    EditorPage(
        title = stringResource(R.string.user_settings_page_general),
        state = state,
        events = events,
        actions = actions,
        canSave = { it.valid },
    ) { draft, enabled ->
        ProfileCard(draft, enabled, actions)
        DiscoverCard(draft, enabled, actions)
        if (draft.canEditQuotas) {
            QuotasCard(draft, enabled, actions)
        }
    }
}

@Composable
private fun ProfileCard(
    draft: GeneralSettings,
    enabled: Boolean,
    actions: EditorActions<GeneralSettings>,
) {
    EditorSectionCard(stringResource(R.string.user_settings_section_profile)) {
        EditorTextField(
            draft.displayName,
            stringResource(R.string.user_settings_display_name),
            icon = Icons.Filled.Person,
            enabled = enabled,
            // What the server itself shows when this is blank, so the example is also the answer to "or what?".
            placeholder = draft.fallbackName.takeIf { it.isNotBlank() },
        ) { value ->
            actions.onEdit { it.copy(displayName = value) }
        }
        EditorTextField(
            draft.email,
            stringResource(R.string.user_settings_email),
            icon = Icons.Filled.Email,
            enabled = enabled && draft.canEditEmail,
            keyboardType = KeyboardType.Email,
            placeholder = stringResource(R.string.placeholder_email),
            supporting = stringResource(R.string.user_settings_email_invalid).takeIf { !draft.emailValid },
            isError = !draft.emailValid,
        ) { value -> actions.onEdit { it.copy(email = value) } }
        EditorTextField(
            draft.discordId,
            stringResource(R.string.user_settings_discord_id),
            icon = Icons.Filled.Forum,
            enabled = enabled,
            keyboardType = KeyboardType.Number,
            supporting = stringResource(R.string.user_settings_discord_id_hint),
            isError = !draft.discordIdValid,
        ) { value -> actions.onEdit { it.copy(discordId = value) } }
    }
}

@Composable
private fun DiscoverCard(
    draft: GeneralSettings,
    enabled: Boolean,
    actions: EditorActions<GeneralSettings>,
) {
    EditorSectionCard(stringResource(R.string.user_settings_section_discover)) {
        EditorTextField(
            draft.locale,
            stringResource(R.string.settings_display_language),
            icon = Icons.Filled.Translate,
            enabled = enabled,
            supporting = stringResource(R.string.user_settings_locale_hint),
            isError = !draft.localeValid,
        ) { value -> actions.onEdit { it.copy(locale = value) } }
        EditorTextField(
            draft.region,
            stringResource(R.string.user_settings_region),
            icon = Icons.Filled.Public,
            enabled = enabled,
            supporting = stringResource(R.string.user_settings_region_hint),
            isError = !draft.regionValid,
        ) { value -> actions.onEdit { it.copy(region = value) } }
        EditorTextField(
            draft.originalLanguage,
            stringResource(R.string.user_settings_original_language),
            icon = Icons.Filled.Language,
            enabled = enabled,
            // The same field as the server-level one, so it says the same thing rather than a second wording of it.
            supporting = stringResource(R.string.server_settings_original_language_hint),
            isError = !draft.originalLanguageValid,
            imeAction = imeActionIf(last = !draft.canEditQuotas),
        ) { value ->
            actions.onEdit { it.copy(originalLanguage = value) }
        }
        draft.watchlistSyncMovies?.let { on ->
            EditorToggleRow(
                editorToggle(Icons.Filled.Bookmark, stringResource(R.string.user_settings_watchlist_movies), on, enabled) { value ->
                    actions.onEdit { it.copy(watchlistSyncMovies = value) }
                },
            )
        }
        draft.watchlistSyncTv?.let { on ->
            EditorToggleRow(
                editorToggle(Icons.Filled.Bookmark, stringResource(R.string.user_settings_watchlist_tv), on, enabled) { value ->
                    actions.onEdit { it.copy(watchlistSyncTv = value) }
                },
            )
        }
    }
}

@Composable
private fun QuotasCard(
    draft: GeneralSettings,
    enabled: Boolean,
    actions: EditorActions<GeneralSettings>,
) {
    EditorSectionCard(stringResource(R.string.user_settings_quotas)) {
        QuotaFields(
            title = stringResource(R.string.hub_quota_movies),
            limit = draft.movieQuotaLimit,
            days = draft.movieQuotaDays,
            default = draft.defaultMovieQuota,
            enabled = enabled,
            onLimit = { value -> actions.onEdit { it.copy(movieQuotaLimit = value) } },
            onDays = { value -> actions.onEdit { it.copy(movieQuotaDays = value) } },
        )
        QuotaFields(
            title = stringResource(R.string.hub_quota_tv),
            limit = draft.tvQuotaLimit,
            days = draft.tvQuotaDays,
            default = draft.defaultTvQuota,
            enabled = enabled,
            onLimit = { value -> actions.onEdit { it.copy(tvQuotaLimit = value) } },
            onDays = { value -> actions.onEdit { it.copy(tvQuotaDays = value) } },
            daysImeAction = ImeAction.Done,
        )
    }
}

/** A limit and its window side by side, under what the server applies when both are blank. */
@Composable
private fun QuotaFields(
    title: String,
    limit: String,
    days: String,
    default: QuotaDefault?,
    enabled: Boolean,
    onLimit: (String) -> Unit,
    onDays: (String) -> Unit,
    daysImeAction: ImeAction = ImeAction.Next,
) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)), modifier = Modifier.fillMaxWidth()) {
        EditorTextField(
            limit,
            stringResource(R.string.user_settings_quota_limit),
            onValueChange = onLimit,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            keyboardType = KeyboardType.Number,
            isError = limit.isNotBlank() && (limit.toIntOrNull() ?: -1) < 0,
        )
        EditorTextField(
            days,
            stringResource(R.string.user_settings_quota_days),
            onValueChange = onDays,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            keyboardType = KeyboardType.Number,
            isError = days.isNotBlank() && (days.toIntOrNull() ?: -1) < 0,
            imeAction = daysImeAction,
        )
    }
    Text(
        stringResource(R.string.user_settings_quota_default, default.label()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun QuotaDefault?.label(): String =
    if (this == null || limit <= 0) {
        stringResource(R.string.settings_request_limit_unlimited)
    } else {
        stringResource(R.string.settings_request_limit_value, limit, days)
    }
