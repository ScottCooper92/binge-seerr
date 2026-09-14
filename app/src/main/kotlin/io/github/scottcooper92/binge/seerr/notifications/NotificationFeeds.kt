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
 * The backstop on the own-requests walk, which is bounded by what it is still watching rather than
 * by a page count. Only a list with something outstanding this far down reaches it (#181).
 */
private const val MAX_OWN_REQUEST_PAGES = 25

/**
 * One feed read: the rows worth announcing, titled, and the newest id the server offered, titled
 * or not. The cursor advances to [newestId], never to the newest announced row.
 */
data class FeedRead<T>(
    val rows: List<T>,
    val newestId: Int,
)

/**
 * One own-requests read: the rows the walk reached, and the watched ids it never got down to.
 *
 * [unaccountedFor] is empty whenever the walk saw the server's last page, because an id missing
 * from a complete list is a request that no longer exists. It is non-empty only where the backstop
 * stopped the walk first, and the caller keeps those ids so the next poll tries for them again.
 */
data class OwnRequestsRead(
    val rows: List<SeerrRequestDto>,
    val unaccountedFor: Set<Int>,
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
         * out of view as the user makes others and its approval is then never seen (#127).
         *
         * [watching] is the ids whose outcome has not been announced yet, and the walk carries on
         * until each has turned up — so a request stays reachable however many the user makes after
         * it, which a fixed page count could not promise (#181). A null [watching] is the seed run
         * and walks the whole list once, to find what was already outstanding before any of this was
         * being tracked.
         *
         * The first [MAX_FEED_PAGES] are read whatever is outstanding, and that is not laziness: the
         * caller rebuilds its per-signal deduplication sets from what this returns, so a walk that
         * stopped shallower than before would prune ids it simply had not looked at and announce
         * them again next time it went deeper.
         */
        suspend fun ownRequests(watching: Set<Int>?): OwnRequestsRead {
            val userId = connection.authenticatedUser().id
            val api = connection.api()
            val outstanding = watching.orEmpty().toMutableSet()
            val seeding = watching == null
            val rows = mutableListOf<SeerrRequestDto>()
            for (pageIndex in 0 until MAX_OWN_REQUEST_PAGES) {
                val page = api.userRequests(userId = userId, take = REQUESTS_PAGE_SIZE, skip = pageIndex * REQUESTS_PAGE_SIZE)
                rows += page.results
                outstanding -= page.results.mapTo(mutableSetOf()) { it.id }
                val lastPage = page.pageInfo.pages > 0 && pageIndex + 1 >= page.pageInfo.pages
                if (page.results.isEmpty() || lastPage) return OwnRequestsRead(rows.distinctBy { it.id }, emptySet())
                if (!seeding && pageIndex + 1 >= MAX_FEED_PAGES && outstanding.isEmpty()) break
                if (pageIndex + 1 == MAX_OWN_REQUEST_PAGES) {
                    Log.w(TAG, "Own requests run past $MAX_OWN_REQUEST_PAGES pages; ${outstanding.size} still unaccounted for")
                }
            }
            return OwnRequestsRead(rows.distinctBy { it.id }, outstanding)
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
