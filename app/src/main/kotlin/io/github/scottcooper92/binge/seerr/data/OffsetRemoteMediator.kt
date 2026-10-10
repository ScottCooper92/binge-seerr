package io.github.scottcooper92.binge.seerr.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** One page as the server sent it, already turned into cache rows: [pages] is its page count, when it sent one. */
class FetchedPage<E>(
    val rows: List<E>,
    val pages: Int?,
)

/**
 * The paging skeleton the request, issue and user lists share. Every open refreshes, so the rows on screen are at most
 * one page stale; further pages append off the cursor the store keeps for [listKey]. A subclass says only how to read
 * one page ([fetch]); the three stores are passed as the three calls the mediator makes on them.
 *
 * Any failure while reading or writing a page becomes a retryable error, or the pager would stop for good.
 */
@OptIn(ExperimentalPagingApi::class)
abstract class OffsetRemoteMediator<E : Any>(
    private val listKey: String,
    private val pageSize: Int,
    private val nextSkip: suspend (String) -> Int?,
    private val refresh: suspend (String, List<E>, Int?) -> Unit,
    private val append: suspend (String, List<E>, Int?) -> Unit,
    /** Told as a refresh starts (null) and once it has written its rows; see [ListRefreshes]. */
    private val onRefresh: (rowsWritten: Int?) -> Unit,
) : RemoteMediator<Int, E>() {
    /** Reads the page starting at [skip], [pageSize] rows long, as cache rows keyed to [listKey]. */
    protected abstract suspend fun fetch(skip: Int): FetchedPage<E>

    override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, E>,
    ): MediatorResult {
        val skip =
            when (loadType) {
                LoadType.REFRESH -> 0
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> nextSkip(listKey) ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        if (loadType == LoadType.REFRESH) onRefresh(null)
        return try {
            val page = fetch(skip)
            val cursor = pageCursorAfter(skip, pageSize, page.pages)
            if (loadType == LoadType.REFRESH) {
                refresh(listKey, page.rows, cursor.nextSkip)
                onRefresh(page.rows.size)
            } else {
                append(listKey, page.rows, cursor.nextSkip)
            }
            MediatorResult.Success(endOfPaginationReached = cursor.endReached)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            MediatorResult.Error(e)
        }
    }
}

/**
 * Turns one page's results into cache rows concurrently, each given its position in the list ([skip] plus its index). A
 * result the app cannot show maps to null and is dropped, rather than failing the page.
 */
internal suspend fun <D, E : Any> List<D>.toRowsConcurrently(
    skip: Int,
    toRow: suspend (D, Int) -> E?,
): List<E> =
    coroutineScope {
        mapIndexed { index, dto -> async { toRow(dto, skip + index) } }.awaitAll().filterNotNull()
    }
