package com.cooper.binge.companion.seerr.ui.tv.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.tv.component.TvConfirmDialogContent
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.FIXED_NOW_MILLIS
import com.cooper.binge.companion.seerr.ui.hub.HubQuota
import com.cooper.binge.companion.seerr.ui.hub.HubQuotaBucket
import com.cooper.binge.companion.seerr.ui.tv.TvDestination
import com.cooper.binge.companion.seerr.ui.tv.TvHomeShell
import com.cooper.binge.companion.seerr.ui.tv.TvTopBarAccount
import com.cooper.binge.companion.seerr.ui.users.UserDetail
import com.cooper.binge.companion.seerr.ui.users.UserDetailUiState
import com.cooper.binge.companion.seerr.ui.users.UserItem
import com.cooper.binge.companion.seerr.ui.users.UserOrigin
import com.binge.designsystem.R as DesR

/** When the account joined, a fixed span before [FIXED_NOW_MILLIS], so the "joined" line does not move with the day the suite runs. */
private const val JOINED_MILLIS = FIXED_NOW_MILLIS - 400L * 24 * 60 * 60 * 1000
private const val ADMIN_PERMISSIONS = 2

private fun detail(
    quota: HubQuota?,
    requestCount: Int = 4,
    id: Int = 7,
) = UserDetailUiState.Ready(
    UserDetail(
        item =
            UserItem(
                id = id,
                name = "Scott",
                email = "scott@example.com",
                handle = "scott",
                avatarUrl = null,
                origin = UserOrigin.Jellyfin,
                permissions = ADMIN_PERMISSIONS,
                requestCount = requestCount,
                createdAtMillis = JOINED_MILLIS,
            ),
        permissions = emptySet(),
        quota = quota,
        watch = null,
        watchlist = emptyList(),
        isSelf = true,
        canEditSettings = true,
        canDelete = false,
        serverUrl = "http://seerr.lan:5055",
        webUrl = "http://seerr.lan:5055/users/$id",
    ),
)

private val PreviewBarAccount = TvTopBarAccount(name = "Scott Cooper", avatarUrl = null)

/**
 * The television account sheet: the profile as one card with a line per request quota (one spent, one part used; or both
 * unlimited) over Sign out, and none for a key connection; the confirm Sign out asks; the sheets before the account is
 * known; and the top bar over Home: the avatar, the Home
 * and Issues tabs, and the Settings gear. The user's own requests are a row on Home, framed on `TvRequestsScreenshotTest`.
 */

class TvAccountScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Sheet() {
        Sheet(
            detail(
                HubQuota(
                    movie = HubQuotaBucket(limit = 5, remaining = 0, days = 7),
                    tv = HubQuotaBucket(limit = 8, remaining = 5, days = 7),
                ),
            ),
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun UnlimitedQuota() {
        Sheet(detail(HubQuota(movie = null, tv = null)))
    }

    /** User 1 is the server's owner: the role line reads Owner, not Admin (#1236). */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Owner() {
        Sheet(detail(HubQuota(movie = null, tv = null), id = 1))
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Loading() {
        Sheet(detail = null)
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Failed() {
        Sheet(detail = UserDetailUiState.Error(SeerrError.Unreachable))
    }

    /** The cached profile before the account loads (#1053): the quota lines hold their place without guessing the numbers. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Seeded() {
        Sheet(UserDetailUiState.Seeded(detail(quota = null).detail.item))
    }

    /** Home's top row: the Account, Issues and Settings buttons in the top-right corner. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun HomeTopRow() {
        TvHomeShell(
            tab = TvDestination.Hub,
            sheet = null,
            onSelectTab = {},
            onOpenSheet = {},
            onCloseSheet = {},
            account = PreviewBarAccount,
        ) { _, _ ->
            Box(modifier = Modifier.fillMaxSize())
        }
    }

    /** A server with issues turned off, or a viewer who may not see them: the top row has no Issues button. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun HomeTopRowWithoutIssues() {
        TvHomeShell(
            tab = TvDestination.Hub,
            sheet = null,
            onSelectTab = {},
            onOpenSheet = {},
            onCloseSheet = {},
            showIssues = false,
            account = PreviewBarAccount,
        ) { _, _ ->
            Box(modifier = Modifier.fillMaxSize())
        }
    }

    /** A connection on the server's API key: there is no session to end, so no Sign out. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ApiKey() {
        Sheet(detail(HubQuota(movie = null, tv = null)), canSignOut = false)
    }

    /** Sign out asks first, in a dialog over the sheet: what it means, then Sign out in the error colour, and Cancel. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun SignOutConfirm() {
        TvConfirmDialogContent(
            title = stringResource(R.string.tv_account_sign_out_title),
            message = stringResource(R.string.tv_account_sign_out_message),
            confirmLabel = stringResource(R.string.tv_account_sign_out),
            dismissLabel = stringResource(DesR.string.action_cancel),
            destructive = true,
            onConfirm = {},
            onDismissRequest = {},
            initiallyDismissFocused = true,
        )
    }

    @Composable
    private fun Sheet(
        detail: UserDetailUiState?,
        canSignOut: Boolean = true,
    ) {
        TvAccountSheet(
            detail = detail,
            accountFailed = detail is UserDetailUiState.Error,
            onRetry = {},
            onDismissRequest = {},
            onSignOut = if (canSignOut) ({}) else null,
        )
    }
}
