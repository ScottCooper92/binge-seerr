package com.cooper.binge.companion.seerr.ui.tv.requests

import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.seerr.seerr.ManageablePermission
import com.cooper.binge.companion.seerr.ui.hub.HubQuotaBucket
import com.cooper.binge.companion.seerr.ui.requests.RequestDetail
import com.cooper.binge.companion.seerr.ui.users.OWNER_USER_ID
import com.cooper.binge.companion.seerr.ui.users.UserDetailUiState
import com.cooper.binge.companion.seerr.ui.users.settings.EditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.ExtrasEditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.GeneralSettings
import com.cooper.binge.companion.seerr.ui.users.settings.PermissionSettings
import com.cooper.binge.companion.seerr.ui.users.settings.QuotaDefault

/** The quota windows the sheet offers, in days: a remote picks one rather than stepping through a month. */
internal val QuotaWindowDays = listOf(1, 3, 7, 14, 30)

/** The highest request limit the sheet's stepper reaches; 0 is unlimited. */
internal const val QUOTA_LIMIT_MAX = 100

/**
 * What the requester sheet shows, put together from the three editors it drives. A null part is one this viewer is not
 * offered here: the server does not offer the permission, or does not let this viewer change this user's quota.
 */
internal data class TvRequesterState(
    val name: String?,
    /** This request's media type's quota, or null where it is unlimited. Only meaningful when [quotaKnown]. */
    val quota: HubQuotaBucket?,
    /** The server has answered with this user's quota; false while it loads, or where this viewer may not read it. */
    val quotaKnown: Boolean,
    val autoApprove: TvRequesterToggle?,
    val request4k: TvRequesterToggle?,
    val override: TvQuotaOverride?,
    val saveStatus: SaveStatus,
)

/** One permission as a row: whether it is held, and whether this viewer may flip it. */
internal data class TvRequesterToggle(
    val checked: Boolean,
    val enabled: Boolean,
)

/** This request's media type's quota override, as the general page holds it. */
internal data class TvQuotaOverride(
    val on: Boolean,
    val limit: Int,
    val days: Int,
    val default: QuotaDefault?,
) {
    /** The windows to offer: the standard ones, and the user's own if the server holds another, so it is never lost. */
    val windowChoices: List<Int> get() = (QuotaWindowDays + days).distinct().sorted()
}

/**
 * Whether the request page offers its requester's sheet: to a viewer who manages users, for a requester who is
 * someone else and not the server's owner, whose settings only the owner changes.
 */
internal fun offersRequesterSheet(detail: RequestDetail): Boolean {
    val requester = detail.item.requestedById ?: return false
    return detail.canManageUsers && requester != OWNER_USER_ID && requester != detail.viewerId
}

/** The sheet's state from the user page, the permissions page and the general page for one user. */
internal fun requesterState(
    isMovie: Boolean,
    user: UserDetailUiState,
    permissions: EditorUiState<PermissionSettings>,
    general: ExtrasEditorUiState<GeneralSettings, *>,
): TvRequesterState {
    val detail = (user as? UserDetailUiState.Ready)?.detail
    val seededName = (user as? UserDetailUiState.Seeded)?.item?.name
    val grants = (permissions as? EditorUiState.Ready)?.draft
    val settings = (general as? ExtrasEditorUiState.Ready)?.draft
    return TvRequesterState(
        name = detail?.item?.name ?: seededName,
        quota = detail?.quota?.let { if (isMovie) it.movie else it.tv },
        quotaKnown = detail?.quota != null,
        autoApprove = grants?.toggle(ManageablePermission.AutoApprove),
        request4k = grants?.toggle(ManageablePermission.Request4k),
        override = settings?.takeIf { it.canEditQuotas }?.override(isMovie),
        saveStatus =
            worst(
                (permissions as? EditorUiState.Ready)?.let { it.saveStatus.orFailed(it.saveFailed) },
                (general as? ExtrasEditorUiState.Ready)?.let { it.saveStatus.orFailed(it.saveFailed) },
            ),
    )
}

private fun PermissionSettings.toggle(permission: ManageablePermission): TvRequesterToggle? =
    permission.takeIf { it in offered }?.let { TvRequesterToggle(checked = it in selected, enabled = it !in locked) }

private fun GeneralSettings.override(isMovie: Boolean): TvQuotaOverride =
    if (isMovie) {
        TvQuotaOverride(movieQuotaOverride, movieQuotaLimit, movieQuotaDays, defaultMovieQuota)
    } else {
        TvQuotaOverride(tvQuotaOverride, tvQuotaLimit, tvQuotaDays, defaultTvQuota)
    }

/** This request's media type's override in [settings] set to [override]. */
internal fun GeneralSettings.withOverride(
    isMovie: Boolean,
    override: TvQuotaOverride,
): GeneralSettings =
    if (isMovie) {
        copy(movieQuotaOverride = override.on, movieQuotaLimit = override.limit, movieQuotaDays = override.days)
    } else {
        copy(tvQuotaOverride = override.on, tvQuotaLimit = override.limit, tvQuotaDays = override.days)
    }

/** The engine reports a failure through its `saveFailed` flag, never through [SaveStatus], as `formSaveStatus` reads it. */
private fun SaveStatus.orFailed(failed: Boolean): SaveStatus = if (failed) SaveStatus.Failed else this

/** The one status line for two editors: a failure first, since it carries the Retry, then a save in flight, then a save done. */
private fun worst(vararg statuses: SaveStatus?): SaveStatus =
    listOf(SaveStatus.Failed, SaveStatus.Saving, SaveStatus.Saved).firstOrNull { it in statuses } ?: SaveStatus.Idle
