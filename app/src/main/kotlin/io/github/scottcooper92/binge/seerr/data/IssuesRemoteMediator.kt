package io.github.scottcooper92.binge.seerr.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrIssueDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

const val ISSUES_PAGE_SIZE = 20

/** What one list asks the server for, and the cache key its pages are stored under. */
data class IssueListQuery(
    val filter: String,
    val sort: String,
    val requestedBy: Int?,
) {
    val listKey: String get() = "$filter:$sort"
}

/**
 * Pages `GET issue` for one [query] into the cache. Every open refreshes, so the rows on screen are
 * at most one page stale; further pages append off the cursor the store keeps. Rows are titled
 * concurrently, and one whose media the app cannot show is dropped rather than failing the page.
 */
@OptIn(ExperimentalPagingApi::class)
class IssuesRemoteMediator(
    private val query: IssueListQuery,
    private val api: suspend () -> SeerrApi,
    private val store: IssueStore,
    /** One row from one issue, titled through the API; null for an issue the app cannot show. */
    private val toEntity: suspend (SeerrIssueDto, SeerrApi, String, Int) -> IssueEntity?,
) : RemoteMediator<Int, IssueEntity>() {
    override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, IssueEntity>,
    ): MediatorResult {
        val skip =
            when (loadType) {
                LoadType.REFRESH -> 0
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> store.nextSkip(query.listKey) ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        return try {
            val api = api()
            val page =
                api.issues(
                    take = ISSUES_PAGE_SIZE,
                    skip = skip,
                    filter = query.filter,
                    sort = query.sort,
                    requestedBy = query.requestedBy,
                )
            val rows =
                coroutineScope {
                    page.results
                        .mapIndexed { index, dto -> async { toEntity(dto, api, query.listKey, skip + index) } }
                        .awaitAll()
                        .filterNotNull()
                }
            val cursor = pageCursorAfter(skip, ISSUES_PAGE_SIZE, page.pageInfo.pages)
            if (loadType == LoadType.REFRESH) {
                store.refresh(query.listKey, rows, cursor.nextSkip)
            } else {
                store.append(query.listKey, rows, cursor.nextSkip)
            }
            MediatorResult.Success(endOfPaginationReached = cursor.endReached)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Any failure here must become a retryable error, or the pager stops for good.
            MediatorResult.Error(e)
        }
    }
}
