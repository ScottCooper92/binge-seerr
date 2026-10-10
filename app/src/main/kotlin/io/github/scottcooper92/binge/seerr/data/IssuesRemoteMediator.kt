package io.github.scottcooper92.binge.seerr.data

import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrIssueDto

const val ISSUES_PAGE_SIZE = 20

/** What one list asks the server for, and the cache key its pages are stored under. */
data class IssueListQuery(
    val filter: String,
    val sort: String,
    val createdBy: Int?,
) {
    /**
     * The scope is part of the key, as it is for requests: rows loaded for a viewer who sees everyone's issues must never read
     * as the list of one who sees their own, should the permission change within a session.
     */
    val listKey: String get() = "$filter:$sort:${createdBy ?: "all"}"
}

/**
 * Pages `GET issue` for one [query] into the cache. Every open refreshes, so the rows on screen are
 * at most one page stale; further pages append off the cursor the store keeps. Rows are titled
 * concurrently, and one whose media the app cannot show is dropped rather than failing the page.
 */
class IssuesRemoteMediator(
    private val query: IssueListQuery,
    private val api: suspend () -> SeerrApi,
    store: IssueStore,
    onRefresh: (rowsWritten: Int?) -> Unit = {},
    /** One row from one issue, titled through the API; null for an issue the app cannot show. */
    private val toEntity: suspend (SeerrIssueDto, SeerrApi, String, Int) -> IssueEntity?,
) : OffsetRemoteMediator<IssueEntity>(
        listKey = query.listKey,
        pageSize = ISSUES_PAGE_SIZE,
        nextSkip = store::nextSkip,
        refresh = store::refresh,
        append = store::append,
        onRefresh = onRefresh,
    ) {
    override suspend fun fetch(skip: Int): FetchedPage<IssueEntity> {
        val api = api()
        val page =
            api.issues(
                take = ISSUES_PAGE_SIZE,
                skip = skip,
                filter = query.filter,
                // query.createdBy is not sent: the server narrows a viewer without MANAGE_ISSUES itself. It keys the cache.
                sort = query.sort,
            )
        return FetchedPage(
            page.results.toRowsConcurrently(skip) { dto, index -> toEntity(dto, api, query.listKey, index) },
            page.pageInfo.pages,
        )
    }
}
