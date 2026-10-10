package io.github.scottcooper92.binge.seerr.ui.tv.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.FIXED_NOW_MILLIS
import io.github.scottcooper92.binge.seerr.ui.FixedSampleRequests
import io.github.scottcooper92.binge.seerr.ui.hub.HubQuota
import io.github.scottcooper92.binge.seerr.ui.hub.HubQuotaBucket
import io.github.scottcooper92.binge.seerr.ui.tv.TvDestination
import io.github.scottcooper92.binge.seerr.ui.tv.TvLoadPhase
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows
import io.github.scottcooper92.binge.seerr.ui.tv.TvShellScaffold
import io.github.scottcooper92.binge.seerr.ui.tv.request
import io.github.scottcooper92.binge.seerr.ui.users.UserDetail
import io.github.scottcooper92.binge.seerr.ui.users.UserDetailUiState
import io.github.scottcooper92.binge.seerr.ui.users.UserItem
import io.github.scottcooper92.binge.seerr.ui.users.UserOrigin

/** When the account joined, a fixed span before [FIXED_NOW_MILLIS], so the "joined" line does not move with the day the suite runs. */
private const val JOINED_MILLIS = FIXED_NOW_MILLIS - 400L * 24 * 60 * 60 * 1000
private const val ADMIN_PERMISSIONS = 2

private val SampleRequests =
    FixedSampleRequests +
        request(4, "Severance", SeerrRequestStatusCode.Approved, mediaStatus = SeerrMediaStatusCode.Available, now = FIXED_NOW_MILLIS)

private fun detail(
    quota: HubQuota?,
    requestCount: Int = SampleRequests.size,
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

/**
 * The television account page: the profile as the phone's user page shows it, a tile per request quota (one spent,
 * one part used; or both unlimited), the user's own requests as a row with a state chip on each poster, the pages
 * before the account is known, and the rail with the account's avatar at its top and Settings on the bottom edge.
 */
class TvAccountScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Board() {
        TvAccountBoard(
            detail =
                detail(
                    HubQuota(
                        movie = HubQuotaBucket(limit = 5, remaining = 0, days = 7),
                        tv = HubQuotaBucket(limit = 8, remaining = 5, days = 7),
                    ),
                ),
            requests = TvPagedRows(count = SampleRequests.size, at = { SampleRequests.getOrNull(it) }),
            onOpenRequest = {},
            onRetry = {},
            onRetryRequests = {},
            overlayOpen = false,
            now = FIXED_NOW_MILLIS,
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun UnlimitedQuota() {
        TvAccountBoard(
            detail = detail(HubQuota(movie = null, tv = null)),
            requests = TvPagedRows(count = SampleRequests.size, at = { SampleRequests.getOrNull(it) }),
            onOpenRequest = {},
            onRetry = {},
            onRetryRequests = {},
            overlayOpen = false,
            now = FIXED_NOW_MILLIS,
        )
    }

    /** More requests than the row holds: the heading carries the whole count and the row ends in a See all tile (#1043). */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun SeeAll() {
        TvAccountBoard(
            detail = detail(HubQuota(movie = null, tv = null), requestCount = 57),
            requests = TvPagedRows(count = SampleRequests.size, at = { SampleRequests.getOrNull(it) }),
            onOpenRequest = {},
            onRetry = {},
            onRetryRequests = {},
            overlayOpen = false,
            now = FIXED_NOW_MILLIS,
        )
    }

    /** User 1 is the server's owner: the role line reads Owner, not Admin (#1236). */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Owner() {
        TvAccountBoard(
            detail = detail(HubQuota(movie = null, tv = null), id = 1),
            requests = TvPagedRows(count = SampleRequests.size, at = { SampleRequests.getOrNull(it) }),
            onOpenRequest = {},
            onRetry = {},
            onRetryRequests = {},
            overlayOpen = false,
            now = FIXED_NOW_MILLIS,
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Loading() {
        TvAccountBoard(
            detail = null,
            requests = TvPagedRows(count = 0, at = { null }),
            onOpenRequest = {},
            onRetry = {},
            onRetryRequests = {},
            overlayOpen = false,
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Failed() {
        TvAccountBoard(
            detail = UserDetailUiState.Error(SeerrError.Unreachable),
            requests = TvPagedRows(count = 0, at = { null }),
            onOpenRequest = {},
            onRetry = {},
            onRetryRequests = {},
            overlayOpen = false,
        )
    }

    /** The user's own requests could not be read: the page says so where the row would be, with a retry (#1035). */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun RequestsFailed() {
        TvAccountBoard(
            detail = detail(HubQuota(movie = null, tv = null)),
            requests = TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Failed),
            onOpenRequest = {},
            onRetry = {},
            onRetryRequests = {},
            overlayOpen = false,
            now = FIXED_NOW_MILLIS,
        )
    }

    /** The rail: the account's avatar as its top item, Home selected, and Settings pinned to the bottom edge. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Rail() {
        TvShellScaffold(selected = TvDestination.Hub, onSelect = {}, accountName = "Scott") {
            Box(modifier = Modifier.fillMaxSize())
        }
    }

    /** The cached profile before the account loads (#1053): the quota tiles hold their place without guessing the numbers. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Seeded() {
        TvAccountBoard(
            detail = UserDetailUiState.Seeded(detail(quota = null).detail.item),
            requests = TvPagedRows(count = SampleRequests.size, at = { SampleRequests.getOrNull(it) }),
            onOpenRequest = {},
            onRetry = {},
            onRetryRequests = {},
            overlayOpen = false,
            now = FIXED_NOW_MILLIS,
        )
    }

    /** A server with issues turned off, or a viewer who may not see them: the rail has no Issues item. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun RailWithoutIssues() {
        TvShellScaffold(selected = TvDestination.Hub, onSelect = {}, accountName = "Scott", showIssues = false) {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}
