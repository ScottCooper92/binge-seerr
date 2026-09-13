package io.github.scottcooper92.binge.seerr.notifications

import android.util.Log
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrPageInfoDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestDto
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.ui.issues.IssueItem
import io.github.scottcooper92.binge.seerr.ui.issues.toIssueItem
import io.github.scottcooper92.binge.seerr.ui.requests.REQUESTS_PAGE_SIZE
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.toRequestItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

private const val TAG = "NotificationFeeds"
private const val FILTER_PENDING = "pending"
private const val FILTER_OPEN = "open"
private const val SORT_ADDED = "added"

/** A backlog wider than this is announced as its newest pages; the older tail is still in the app. */
private const val MAX_FEED_PAGES = 5

/**
 * The reads the poll makes. A feed is paged newest-first and only the rows past the cursor are
 * titled, so a steady-state poll with nothing new titles nothing; a null cursor is a seed run,
 * answering the newest page for the caller to read the latest id from.
 */
class NotificationFeeds
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val titles: TitleCache,
    ) {
        private val now: () -> Long = System::currentTimeMillis

        suspend fun pendingRequests(sinceId: Int?): List<RequestItem> {
            val api = connection.api()
            val fresh =
                collectFreshRows(sinceId, { it.id }) { skip ->
                    val page = api.requests(take = REQUESTS_PAGE_SIZE, skip = skip, filter = FILTER_PENDING, sort = SORT_ADDED)
                    page.results to page.pageInfo
                }
            return fresh.titled { it.toRequestItem(api, titles::get, now()) }
        }

        suspend fun openIssues(sinceId: Int?): List<IssueItem> {
            val api = connection.api()
            val fresh =
                collectFreshRows(sinceId, { it.id }) { skip ->
                    val page = api.issues(take = REQUESTS_PAGE_SIZE, skip = skip, filter = FILTER_OPEN, sort = SORT_ADDED)
                    page.results to page.pageInfo
                }
            return fresh.titled { it.toIssueItem(api, titles::get) }
        }

        /** The user's own newest requests, untitled: the caller decides which changed state before titling. */
        suspend fun ownRequests(): List<SeerrRequestDto> {
            val userId = connection.authenticatedUser().id
            return connection.api().userRequests(userId = userId, take = REQUESTS_PAGE_SIZE).results
        }

        suspend fun titled(rows: List<SeerrRequestDto>): List<RequestItem> {
            val api = connection.api()
            return rows.titled { it.toRequestItem(api, titles::get, now()) }
        }

        private suspend fun <D, T : Any> List<D>.titled(map: suspend (D) -> T?): List<T> =
            coroutineScope { map { row -> async { map(row) } }.awaitAll().filterNotNull() }

        /**
         * Stops at a row already seen, the last page, or the page cap. Offset paging can show a
         * boundary row twice when the list shifts between fetches, so the rows are deduplicated by id.
         */
        private suspend fun <D> collectFreshRows(
            sinceId: Int?,
            idOf: (D) -> Int,
            fetchPage: suspend (skip: Int) -> Pair<List<D>, SeerrPageInfoDto>,
        ): List<D> {
            val fresh = mutableListOf<D>()
            var reachedEnd = false
            for (pageIndex in 0 until MAX_FEED_PAGES) {
                val (results, pageInfo) = fetchPage(pageIndex * REQUESTS_PAGE_SIZE)
                if (sinceId == null) return results
                fresh += results.filter { idOf(it) > sinceId }
                val reachedSeen = results.any { idOf(it) <= sinceId }
                val lastPage = pageInfo.pages > 0 && pageIndex + 1 >= pageInfo.pages
                if (results.isEmpty() || reachedSeen || lastPage) {
                    reachedEnd = true
                    break
                }
            }
            if (!reachedEnd) Log.w(TAG, "A backlog wider than $MAX_FEED_PAGES pages; the older rows are not announced")
            return fresh.distinctBy(idOf)
        }
    }
