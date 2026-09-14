package io.github.scottcooper92.binge.seerr.notifications

import android.util.Log
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrIssueDto
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

/** How far back any of the poll's reads walks; past it the older rows are not seen, but are in the app. */
private const val MAX_FEED_PAGES = 5

/**
 * One feed read: the rows worth announcing, titled, and the newest id the server offered, titled
 * or not. The cursor advances to [newestId], never to the newest announced row.
 */
data class FeedRead<T>(
    val rows: List<T>,
    val newestId: Int,
)

/**
 * The reads the poll makes. A feed is paged newest-first and only the rows past the cursor are
 * titled, so a steady-state poll with nothing new titles nothing. Seeding is its own call, which
 * reads the newest id and titles nothing at all.
 */
class NotificationFeeds
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val titles: TitleCache,
    ) {
        private val now: () -> Long = System::currentTimeMillis

        suspend fun seedPendingRequests(): Int = newestId({ it.id }, ::pendingRequestPage)

        suspend fun pendingRequests(sinceId: Int): FeedRead<RequestItem> {
            val api = connection.api()
            val fresh = collectFreshRows(sinceId, { it.id }, ::pendingRequestPage)
            return FeedRead(fresh.rows.titled { it.toRequestItem(api, titles::get, now()) }, fresh.newestId)
        }

        suspend fun seedOpenIssues(): Int = newestId({ it.id }, ::openIssuePage)

        suspend fun openIssues(sinceId: Int): FeedRead<IssueItem> {
            val api = connection.api()
            val fresh = collectFreshRows(sinceId, { it.id }, ::openIssuePage)
            return FeedRead(fresh.rows.titled { it.toIssueItem(api, titles::get) }, fresh.newestId)
        }

        private suspend fun pendingRequestPage(skip: Int): Pair<List<SeerrRequestDto>, SeerrPageInfoDto> =
            connection
                .api()
                .requests(take = REQUESTS_PAGE_SIZE, skip = skip, filter = FILTER_PENDING, sort = SORT_ADDED)
                .let { it.results to it.pageInfo }

        private suspend fun openIssuePage(skip: Int): Pair<List<SeerrIssueDto>, SeerrPageInfoDto> =
            connection
                .api()
                .issues(take = REQUESTS_PAGE_SIZE, skip = skip, filter = FILTER_OPEN, sort = SORT_ADDED)
                .let { it.results to it.pageInfo }

        /**
         * The user's own requests, untitled: the caller decides which changed state before titling.
         *
         * Paged, and unlike a feed not against a cursor. A feed is append-only, so what is new is at
         * the front and a cursor can stop the walk; a request instead changes state where it already
         * sits in a list ordered newest-first. Read as one page, a request awaiting approval slides
         * out of view as the user makes others and its approval is then never seen (#127). The walk
         * stops at the server's last page, so a short history still costs one call.
         */
        suspend fun ownRequests(): List<SeerrRequestDto> {
            val userId = connection.authenticatedUser().id
            val api = connection.api()
            return collectRows({ it.id }) { skip ->
                val page = api.userRequests(userId = userId, take = REQUESTS_PAGE_SIZE, skip = skip)
                page.results to page.pageInfo
            }.rows
        }

        suspend fun titled(rows: List<SeerrRequestDto>): List<RequestItem> {
            val api = connection.api()
            return rows.titled { it.toRequestItem(api, titles::get, now()) }
        }

        private suspend fun <D, T : Any> List<D>.titled(map: suspend (D) -> T?): List<T> =
            coroutineScope { map { row -> async { map(row) } }.awaitAll().filterNotNull() }

        /**
         * The newest id the feed carries, read from its first page and nothing else. A seed run
         * wants only this, so it titles nothing: the rows are discarded, and a title lookup per row
         * is a TMDB call each (#125).
         */
        private suspend fun <D> newestId(
            idOf: (D) -> Int,
            fetchPage: suspend (skip: Int) -> Pair<List<D>, SeerrPageInfoDto>,
        ): Int = fetchPage(0).first.maxOfOrNull(idOf) ?: 0

        /**
         * The rows past the cursor, up to one already seen, with the newest **raw** id alongside.
         * The cursor advances to that rather than to the newest announced row: titling drops a row
         * whose media maps to neither movie nor tv, and a dropped newest row would otherwise hold
         * the cursor below it for good (#126).
         */
        private suspend fun <D> collectFreshRows(
            sinceId: Int,
            idOf: (D) -> Int,
            fetchPage: suspend (skip: Int) -> Pair<List<D>, SeerrPageInfoDto>,
        ): FeedRead<D> =
            collectRows(
                idOf = idOf,
                keep = { idOf(it) > sinceId },
                doneAfter = { page -> page.any { idOf(it) <= sinceId } },
                fetchPage = fetchPage,
            ).let { FeedRead(it.rows, maxOf(sinceId, it.newestId)) }

        /**
         * Pages newest-first, keeping what [keep] accepts, and stops at [doneAfter], the server's last
         * page or [MAX_FEED_PAGES]. Offset paging can show a boundary row twice when the list shifts
         * between fetches, so the rows are deduplicated by id. The newest id is tracked over every
         * row the server answered with, kept or not, which is what a cursor must follow.
         */
        private suspend fun <D> collectRows(
            idOf: (D) -> Int,
            keep: (D) -> Boolean = { true },
            doneAfter: (List<D>) -> Boolean = { false },
            fetchPage: suspend (skip: Int) -> Pair<List<D>, SeerrPageInfoDto>,
        ): FeedRead<D> {
            val rows = mutableListOf<D>()
            var newestId = 0
            for (pageIndex in 0 until MAX_FEED_PAGES) {
                val (results, pageInfo) = fetchPage(pageIndex * REQUESTS_PAGE_SIZE)
                rows += results.filter(keep)
                newestId = maxOf(newestId, results.maxOfOrNull(idOf) ?: 0)
                val lastPage = pageInfo.pages > 0 && pageIndex + 1 >= pageInfo.pages
                if (results.isEmpty() || lastPage || doneAfter(results)) break
                if (pageIndex + 1 == MAX_FEED_PAGES) {
                    Log.w(TAG, "A list wider than $MAX_FEED_PAGES pages; the older rows are not read")
                }
            }
            return FeedRead(rows.distinctBy(idOf), newestId)
        }
    }
