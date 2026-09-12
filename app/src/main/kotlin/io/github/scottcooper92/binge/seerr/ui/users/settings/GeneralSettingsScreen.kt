package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
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
        canSave = { it.quotasValid },
    ) { draft, enabled ->
        EditorTextField(draft.displayName, stringResource(R.string.user_settings_display_name), enabled = enabled) { value ->
            actions.onEdit { it.copy(displayName = value) }
        }
        EditorTextField(
            draft.email,
            stringResource(R.string.user_settings_email),
            enabled = enabled && draft.canEditEmail,
            keyboardType = KeyboardType.Email,
        ) { value -> actions.onEdit { it.copy(email = value) } }
        EditorTextField(
            draft.discordId,
            stringResource(R.string.user_settings_discord_id),
            enabled = enabled,
            keyboardType = KeyboardType.Number,
        ) { value -> actions.onEdit { it.copy(discordId = value) } }
        EditorSectionTitle(stringResource(R.string.user_settings_section_discover))
        EditorTextField(
            draft.locale,
            stringResource(R.string.settings_display_language),
            enabled = enabled,
            supporting = stringResource(R.string.user_settings_locale_hint),
        ) { value -> actions.onEdit { it.copy(locale = value) } }
        EditorTextField(
            draft.region,
            stringResource(R.string.user_settings_region),
            enabled = enabled,
            supporting = stringResource(R.string.user_settings_region_hint),
        ) { value -> actions.onEdit { it.copy(region = value) } }
        EditorTextField(draft.originalLanguage, stringResource(R.string.user_settings_original_language), enabled = enabled) { value ->
            actions.onEdit { it.copy(originalLanguage = value) }
        }
        draft.watchlistSyncMovies?.let { on ->
            EditorSwitchRow(stringResource(R.string.user_settings_watchlist_movies), on, enabled = enabled) { value ->
                actions.onEdit { it.copy(watchlistSyncMovies = value) }
            }
        }
        draft.watchlistSyncTv?.let { on ->
            EditorSwitchRow(stringResource(R.string.user_settings_watchlist_tv), on, enabled = enabled) { value ->
                actions.onEdit { it.copy(watchlistSyncTv = value) }
            }
        }
        if (draft.canEditQuotas) {
            EditorSectionTitle(stringResource(R.string.user_settings_quotas))
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
            )
        }
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
