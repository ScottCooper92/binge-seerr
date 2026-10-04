package io.github.scottcooper92.binge.seerr.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestDto
import io.github.scottcooper92.binge.seerr.ui.requests.REQUESTS_PAGE_SIZE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
@OptIn(ExperimentalPagingApi::class)
class RequestsRemoteMediator(
    private val query: RequestListQuery,
    private val api: suspend () -> SeerrApi,
    private val store: RequestStore,
    /** Told as a refresh starts (null) and once it has written its rows; see [ListRefreshes]. */
    private val onRefresh: (rowsWritten: Int?) -> Unit = {},
    /** One row from one request, titled through the API; null for a request the app cannot show. */
    private val toEntity: suspend (SeerrRequestDto, SeerrApi, String, Int) -> RequestEntity?,
) : RemoteMediator<Int, RequestEntity>() {
    override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, RequestEntity>,
    ): MediatorResult {
        val skip =
            when (loadType) {
                LoadType.REFRESH -> 0
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> store.nextSkip(query.listKey) ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        if (loadType == LoadType.REFRESH) onRefresh(null)
        return try {
            val api = api()
            val page =
                api.requests(
                    take = REQUESTS_PAGE_SIZE,
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
            val cursor = pageCursorAfter(skip, REQUESTS_PAGE_SIZE, page.pageInfo.pages)
            if (loadType == LoadType.REFRESH) {
                store.refresh(query.listKey, rows, cursor.nextSkip)
                onRefresh(rows.size)
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
