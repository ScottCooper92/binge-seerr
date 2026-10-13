package com.cooper.binge.companion.seerr.ui.users

import com.binge.companion.sdk.paging.OffsetPage
import com.binge.companion.sdk.paging.OffsetPagingSource
import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.ui.requests.REQUESTS_PAGE_SIZE
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import com.cooper.binge.companion.seerr.ui.requests.toRequestPage

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
