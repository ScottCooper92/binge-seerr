package io.github.scottcooper92.binge.seerr.ui.users

import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.ui.hub.HubQuota
import io.github.scottcooper92.binge.seerr.ui.hub.HubQuotaBucket

private const val USER_ID = 7
private const val CREATED_AT_MILLIS = 1_749_000_000_000L
private const val QUOTA_LIMIT = 10
private const val QUOTA_REMAINING = 6
private const val QUOTA_DAYS = 7
private const val PLAY_COUNT = 42

/**
 * A manager's own account: quota, permissions and watch data, all populated. The Requests section
 * itself has no fixture data to show — `collectAsLazyPagingItems()` starts in `Loading` until its
 * collection coroutine gets a dispatch, which a static screenshot render never provides, so its rows
 * are never reachable from a `@PreviewTest` frame no matter what [UserDetailScreenshotTest] passes for
 * `requests`. The frame still earns its keep on everything above that section.
 */
internal fun manageableUserDetail(): UserDetail =
    UserDetail(
        item =
            UserItem(
                id = USER_ID,
                name = "Ada Lovelace",
                email = "ada@example.com",
                handle = "ada",
                avatarUrl = null,
                origin = UserOrigin.Plex,
                permissions = ManageablePermission.Admin.bit,
                requestCount = 1,
                createdAtMillis = CREATED_AT_MILLIS,
            ),
        permissions = setOf(ManageablePermission.Admin),
        quota = HubQuota(movie = HubQuotaBucket(limit = QUOTA_LIMIT, remaining = QUOTA_REMAINING, days = QUOTA_DAYS), tv = null),
        watch = UserWatch(playCount = PLAY_COUNT, recentlyWatched = emptyList()),
        watchlist = emptyList(),
        isSelf = false,
        canEditSettings = true,
        canDelete = true,
        serverUrl = "https://seerr.example/",
        webUrl = "https://seerr.example/users/7",
    )
