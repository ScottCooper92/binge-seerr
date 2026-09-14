package io.github.scottcooper92.binge.seerr.ui.users

import io.github.scottcooper92.binge.seerr.seerr.HydratedTitle
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.ui.requests.OffsetPage
import io.github.scottcooper92.binge.seerr.ui.requests.OffsetPagingSource
import io.github.scottcooper92.binge.seerr.ui.requests.REQUESTS_PAGE_SIZE
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.toRequestPage

/**
 * Pages one user's own requests, newest first, each row titled through [hydrate] as the browser's
 * are. The dedicated endpoint, not `GET request?requestedBy=`: this page opens for a viewer with
 * `MANAGE_USERS`, who need not also manage requests.
 */
class UserRequestsPagingSource(
    private val userId: Int,
    private val api: suspend () -> SeerrApi,
    private val hydrate: suspend (SeerrApi, String, Int) -> HydratedTitle?,
    private val now: () -> Long = System::currentTimeMillis,
) : OffsetPagingSource<RequestItem>(REQUESTS_PAGE_SIZE) {
    override suspend fun loadPage(
        take: Int,
        skip: Int,
    ): OffsetPage<RequestItem> {
        val api = api()
        return api.userRequests(userId = userId, take = take, skip = skip).toRequestPage(api, hydrate, now())
    }
}
