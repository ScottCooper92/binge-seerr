package io.github.scottcooper92.binge.seerr.ui.users

import io.github.scottcooper92.binge.seerr.seerr.HydratedTitle
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.ui.requests.OffsetPage
import io.github.scottcooper92.binge.seerr.ui.requests.OffsetPagingSource
import io.github.scottcooper92.binge.seerr.ui.requests.REQUESTS_PAGE_SIZE
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.toRequestItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Pages one user's own requests, newest first, each row titled through [hydrate] as the browser's are. */
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
        val page = api.userRequests(userId = userId, take = take, skip = skip)
        val items =
            coroutineScope {
                page.results
                    .map { dto -> async { dto.toRequestItem(api, hydrate, now()) } }
                    .awaitAll()
                    .filterNotNull()
            }
        return OffsetPage(items, page.pageInfo.pages.takeIf { it > 0 })
    }
}
