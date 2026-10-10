package io.github.scottcooper92.binge.seerr.data

import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestDto
import io.github.scottcooper92.binge.seerr.ui.requests.REQUESTS_PAGE_SIZE

/** What one list asks the server for, and the cache key its pages are stored under. */
data class RequestListQuery(
    val filter: String,
    val sort: String,
    val requestedBy: Int?,
) {
    /** The scope is in the key so rows loaded for one viewer's permissions never read as another's. */
    val listKey: String get() = "$filter:$sort:${requestedBy ?: "all"}"
}

/**
 * Pages `GET request` for one [query] into the cache. Every open refreshes, so the rows on screen
 * are at most one page stale; further pages append off the cursor the store keeps. Rows are titled
 * concurrently, and one whose media the app cannot show is dropped rather than failing the page.
 */
class RequestsRemoteMediator(
    private val query: RequestListQuery,
    private val api: suspend () -> SeerrApi,
    store: RequestStore,
    onRefresh: (rowsWritten: Int?) -> Unit = {},
    /** One row from one request, titled through the API; null for a request the app cannot show. */
    private val toEntity: suspend (SeerrRequestDto, SeerrApi, String, Int) -> RequestEntity?,
) : OffsetRemoteMediator<RequestEntity>(
        listKey = query.listKey,
        pageSize = REQUESTS_PAGE_SIZE,
        nextSkip = store::nextSkip,
        refresh = store::refresh,
        append = store::append,
        onRefresh = onRefresh,
    ) {
    override suspend fun fetch(skip: Int): FetchedPage<RequestEntity> {
        val api = api()
        val page =
            api.requests(
                take = REQUESTS_PAGE_SIZE,
                skip = skip,
                filter = query.filter,
                sort = query.sort,
                requestedBy = query.requestedBy,
            )
        return FetchedPage(
            page.results.toRowsConcurrently(skip) { dto, index -> toEntity(dto, api, query.listKey, index) },
            page.pageInfo.pages,
        )
    }
}
