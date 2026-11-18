package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.request.RequestQuota
import com.cooper.binge.companion.seerr.discover.request.SeasonSelection

/**
 * The one group of options: Seasons for a show, 4K where this user may ask for it, Advanced options where they may choose
 * a destination. The quota the request would use sits above Advanced options, so what it opens lands directly under it. A row the user has no use for is not drawn.
 */
@Composable
internal fun RequestOptionsGroup(
    state: RequestSheetUiState.Ready,
    actions: RequestSheetActions,
    onOpenSeasons: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows =
        listOfNotNull(
            state.seasons?.let { seasonsRow(it, state.submitting, actions, onOpenSeasons) },
            fourKRow(state, actions),
            quotaRow(state, actions),
            advancedRow(state, actions),
        )
    if (rows.isNotEmpty()) ItemGroup(title = null, rows = rows, modifier = modifier)
}

@Composable
private fun seasonsRow(
    selection: SeasonSelection,
    submitting: Boolean,
    actions: RequestSheetActions,
    onOpenSeasons: () -> Unit,
): ListItem {
    val checkbox =
        when {
            !selection.partialAllowed || selection.allOpenPicked -> ToggleableState.On
            selection.picked.isEmpty() -> ToggleableState.Off
            else -> ToggleableState.Indeterminate
        }
    val detail =
        if (selection.partialAllowed) {
            stringResource(R.string.request_sheet_seasons_count, selection.requestSeasons.size, selection.open.size)
        } else {
            stringResource(R.string.request_sheet_remaining_seasons)
        }
    return ListItem(
        icon = Icons.Filled.DoneAll,
        label = stringResource(R.string.request_sheet_seasons),
        detail = detail,
        enabled = !submitting,
        onClick = onOpenSeasons,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selection.partialAllowed) {
                    TriStateCheckbox(state = checkbox, onClick = actions.onAllSeasons, enabled = !submitting && selection.canPickAll)
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun fourKRow(
    state: RequestSheetUiState.Ready,
    actions: RequestSheetActions,
): ListItem? {
    if (!state.access.may4k || !state.target.fourKOpen) return null
    return ListItem(
        icon = Icons.Filled.Hd,
        label = stringResource(R.string.request_sheet_4k),
        toggled = state.use4k,
        enabled = state.canToggle4k,
        onClick = { actions.onUse4k(!state.use4k) },
    )
}

@Composable
private fun advancedRow(
    state: RequestSheetUiState.Ready,
    actions: RequestSheetActions,
): ListItem? {
    if (!state.showAdvanced) return null
    return ListItem(
        icon = Icons.Filled.Settings,
        label = stringResource(R.string.request_sheet_advanced),
        expanded = state.advancedExpanded,
        enabled = !state.submitting,
        onClick = { actions.onAdvancedExpanded(!state.advancedExpanded) },
    )
}

/** The quota the request would use. A read that failed says so, and a tap reads it again; an unmetered kind has no row. */
@Composable
private fun quotaRow(
    state: RequestSheetUiState.Ready,
    actions: RequestSheetActions,
): ListItem? =
    when (val quota = state.quota) {
        QuotaState.Loading ->
            ListItem(
                icon = Icons.Filled.Download,
                label = stringResource(R.string.request_sheet_quota_loading),
                clickable = false,
                loading = true,
            )
        QuotaState.Failed ->
            ListItem(
                icon = Icons.Filled.Download,
                label = stringResource(R.string.request_sheet_quota_failed),
                detail = stringResource(R.string.action_try_again),
                detailColor = MaterialTheme.colorScheme.error,
                onClick = actions.onRetryQuota,
            )
        is QuotaState.Known -> quota.quota?.let { quotaRow(it) }
    }

@Composable
private fun quotaRow(quota: RequestQuota): ListItem {
    val left = pluralStringResource(R.plurals.hub_quota_remaining, quota.remaining, quota.remaining, quota.limit)
    val period = quota.days?.let { pluralStringResource(R.plurals.hub_quota_period, it, it) }
    return ListItem(
        icon = Icons.Filled.Download,
        label = left,
        detail = if (quota.spent) stringResource(R.string.request_sheet_quota_spent) else period,
        detailColor = if (quota.spent) MaterialTheme.colorScheme.error else null,
        supportingMaxLines = 2,
        clickable = false,
    )
}
