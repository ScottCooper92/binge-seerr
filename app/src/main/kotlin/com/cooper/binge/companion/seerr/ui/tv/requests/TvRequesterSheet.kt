package com.cooper.binge.companion.seerr.ui.tv.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.tv.component.TvCheckboxRow
import com.binge.designsystem.tv.component.TvNumberStepper
import com.binge.designsystem.tv.component.TvOptionGroup
import com.binge.designsystem.tv.component.TvSaveStatus
import com.binge.designsystem.tv.component.TvSideSheetBody
import com.binge.designsystem.tv.component.TvSideSheetStepFocus
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.ManageablePermission
import com.cooper.binge.companion.seerr.ui.hub.HubQuotaBucket
import com.cooper.binge.companion.seerr.ui.users.UserDetailViewModel
import com.cooper.binge.companion.seerr.ui.users.settings.EditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.ExtrasEditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.GeneralSettingsViewModel
import com.cooper.binge.companion.seerr.ui.users.settings.PermissionsViewModel
import com.cooper.binge.companion.seerr.ui.users.settings.QuotaDefault
import com.binge.designsystem.R as DesR

/** What the requester sheet can be asked to do, as the user's own settings pages would do it. */
internal class TvRequesterActions(
    val onToggleAutoApprove: () -> Unit,
    val onToggleRequest4k: () -> Unit,
    val onOverrideChange: (TvQuotaOverride) -> Unit,
    val onRetrySave: () -> Unit,
)

/**
 * The request's requester, opened from the request page: who they are and what is left of this request's media type's
 * quota, Auto-approve and Request 4K, and that quota's override, each saved as it is made. The rest of the user's settings
 * stay on the phone, and the sheet says so. Every control routes through the same editors as the phone's user pages, so
 * the server's rules are the same: a permission only the owner may change is shown and locked.
 */
@Composable
internal fun ColumnScope.TvRequesterStep(
    state: TvRequesterState,
    isMovie: Boolean,
    actions: TvRequesterActions,
    entryFocus: FocusRequester,
) {
    TvSideSheetTitle(state.name ?: stringResource(R.string.tv_detail_requester))
    quotaLine(state, isMovie)?.let { TvSideSheetBody(it) }
    Column(
        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        state.autoApprove?.let {
            TvCheckboxRow(
                label = stringResource(R.string.permission_auto_approve),
                checked = it.checked,
                enabled = it.enabled,
                onCheckedChange = { actions.onToggleAutoApprove() },
                modifier = Modifier.focusRequester(entryFocus),
            )
        }
        state.request4k?.let {
            TvCheckboxRow(
                label = stringResource(R.string.permission_request_4k),
                checked = it.checked,
                enabled = it.enabled,
                onCheckedChange = { actions.onToggleRequest4k() },
                modifier = if (state.autoApprove == null) Modifier.focusRequester(entryFocus) else Modifier,
            )
        }
        state.override?.let { override ->
            TvQuotaOverrideRows(
                override = override,
                isMovie = isMovie,
                onChange = actions.onOverrideChange,
                modifier = if (state.autoApprove == null && state.request4k == null) Modifier.focusRequester(entryFocus) else Modifier,
            )
        }
    }
    // Under the rows rather than among them, so a failed save's Retry stays in view however far the override runs.
    TvSaveStatus(status = state.saveStatus, onRetry = actions.onRetrySave)
    TvSideSheetBody(stringResource(R.string.tv_requester_phone))
    TvSideSheetStepFocus(entryFocus)
}

/** The override's switch, then while it is on, the limit and the window it counts over. */
@Composable
private fun TvQuotaOverrideRows(
    override: TvQuotaOverride,
    isMovie: Boolean,
    onChange: (TvQuotaOverride) -> Unit,
    modifier: Modifier,
) {
    TvCheckboxRow(
        label =
            stringResource(
                if (isMovie) R.string.user_settings_quota_override_movie else R.string.user_settings_quota_override_tv,
            ),
        checked = override.on,
        onCheckedChange = { onChange(override.copy(on = it)) },
        supportingText = stringResource(R.string.user_settings_quota_default, override.default.label()),
        modifier = modifier,
    )
    if (!override.on) return
    TvSideSheetBody(stringResource(R.string.tv_requester_limit_title))
    TvNumberStepper(
        value = override.limit,
        onValueChange = { onChange(override.copy(limit = it)) },
        range = 0..QUOTA_LIMIT_MAX,
        valueLabel =
            if (override.limit == 0) {
                stringResource(R.string.hub_quota_unlimited)
            } else {
                pluralStringResource(R.plurals.tv_requester_limit_value, override.limit, override.limit)
            },
    )
    TvOptionGroup(
        title = stringResource(R.string.tv_requester_window_title),
        choices = override.windowChoices.map { it to pluralStringResource(R.plurals.hub_quota_period, it, it) },
        selected = override.days,
        onSelect = { onChange(override.copy(days = it)) },
    )
}

/**
 * This request's media type's quota as a line: what is left of the limit and over what window, or that there is none.
 * Null until the server has answered, and where this viewer may not read it, so an unknown quota never reads as unlimited.
 */
@Composable
private fun quotaLine(
    state: TvRequesterState,
    isMovie: Boolean,
): String? {
    if (!state.quotaKnown) return null
    val type = stringResource(if (isMovie) R.string.hub_quota_movies else R.string.hub_quota_tv)
    val bucket: HubQuotaBucket? = state.quota
    val amount =
        when {
            bucket == null || bucket.limit <= 0 -> stringResource(R.string.hub_quota_unlimited)
            else ->
                listOfNotNull(
                    pluralStringResource(R.plurals.hub_quota_remaining, bucket.remaining, bucket.remaining, bucket.limit),
                    bucket.days?.let { pluralStringResource(R.plurals.hub_quota_period, it, it) },
                ).joinToString(stringResource(R.string.hub_meta_separator))
        }
    return stringResource(R.string.tv_requester_quota, type, amount)
}

@Composable
private fun QuotaDefault?.label(): String =
    if (this == null || limit <= 0) {
        stringResource(R.string.settings_request_limit_unlimited)
    } else {
        stringResource(R.string.settings_request_limit_value, limit, days)
    }

/**
 * The sheet over the requester's own user page, permissions page and general page, under keys of its own so it does not
 * share an editor with a page the phone has open. Each editor saves as it changes, on the application's scope, so a change
 * made just before the sheet closes still lands.
 */
@Composable
internal fun ColumnScope.TvRequesterSheetEntry(
    userId: Int,
    isMovie: Boolean,
    entryFocus: FocusRequester,
) {
    val user =
        hiltViewModel<UserDetailViewModel, UserDetailViewModel.Factory>(
            key = "requester-user-$userId",
            creationCallback = { it.create(userId) },
        )
    val permissions =
        hiltViewModel<PermissionsViewModel, PermissionsViewModel.Factory>(
            key = "requester-permissions-$userId",
            creationCallback = { it.create(userId) },
        )
    val general =
        hiltViewModel<GeneralSettingsViewModel, GeneralSettingsViewModel.Factory>(
            key = "requester-general-$userId",
            creationCallback = { it.create(userId) },
        )
    DisposableEffect(user) {
        user.setScreenVisible(true)
        onDispose { user.setScreenVisible(false) }
    }
    val userState by user.uiState.collectAsStateWithLifecycle()
    val permissionsState by permissions.uiState.collectAsStateWithLifecycle()
    val generalState by general.uiState.collectAsStateWithLifecycle()
    TvRequesterStep(
        state = requesterState(isMovie, userState, permissionsState, generalState),
        isMovie = isMovie,
        actions =
            TvRequesterActions(
                onToggleAutoApprove = { permissions.toggle(ManageablePermission.AutoApprove) },
                onToggleRequest4k = { permissions.toggle(ManageablePermission.Request4k) },
                onOverrideChange = { override -> general.edit { it.withOverride(isMovie, override) } },
                onRetrySave = {
                    if ((permissionsState as? EditorUiState.Ready)?.saveFailed == true) permissions.save()
                    if ((generalState as? ExtrasEditorUiState.Ready)?.saveFailed == true) general.save()
                },
            ),
        entryFocus = entryFocus,
    )
}
