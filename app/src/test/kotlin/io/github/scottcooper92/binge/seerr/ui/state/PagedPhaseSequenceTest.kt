package io.github.scottcooper92.binge.seerr.ui.state

import androidx.paging.CombinedLoadStates
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import com.binge.designsystem.template.PagedPhase
import com.binge.designsystem.template.PagedPhaseTracker
import io.github.scottcooper92.binge.seerr.data.CacheDatabaseRule
import io.github.scottcooper92.binge.seerr.data.ListRefresh
import io.github.scottcooper92.binge.seerr.data.ListRefreshes
import io.github.scottcooper92.binge.seerr.data.RequestEntity
import io.github.scottcooper92.binge.seerr.data.RequestListQuery
import io.github.scottcooper92.binge.seerr.data.RequestsRemoteMediator
import io.github.scottcooper92.binge.seerr.data.RoomRequestStore
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.ui.requests.REQUESTS_PAGE_SIZE
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A hang guard, not a speed limit: it runs in wall-clock time under `runBlocking`, and the first test in the class also pays
 * for Robolectric, Room, Paging and OkHttp starting cold, which a busy CI runner can stretch past 10 s (#806).
 */
private const val TIMEOUT_MILLIS = 60_000L

private val rows = PagedPhase.Rows(refreshing = false, refreshError = null)
private val refreshingRows = PagedPhase.Rows(refreshing = true, refreshError = null)

/**
 * The real pipeline under a list screen: the requests mediator over a socket, writing to the real
 * cache in memory, read back through Room's paging source. Every state the pager reports is recorded
 * with the row count and mapped through [PagedPhaseTracker], as the screen would map it.
 *
 * Mapped through the decision the screens made before, a cold open of a non-empty list read
 * `[Empty, Skeleton, Empty, Rows]`: once as the pager started, because the cache's own read in
 * progress was ignored, and once in the gap between the mediator finishing and Room handing over the
 * rows it wrote.
 */
@OptIn(ExperimentalPagingApi::class)
@RunWith(RobolectricTestRunner::class)
class PagedPhaseSequenceTest {
    @get:Rule
    val cache = CacheDatabaseRule()

    private val server = MockWebServer()

    @After
    fun tearDown() = server.close()

    private fun serve(
        ids: List<Int>,
        pages: Int = if (ids.isEmpty()) 0 else 1,
    ) {
        val results = ids.joinToString { id -> """{ "id": $id, "status": 1, "media": { "tmdbId": $id, "mediaType": "movie" } }""" }
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse(
                        headers = headersOf("Content-Type", "application/json"),
                        body = """{ "pageInfo": { "pages": $pages, "results": ${ids.size} }, "results": [ $results ] }""",
                    )
            }
        server.start()
    }

    private fun api(): SeerrApi = SeerrApiFactory(logRequests = false).cached(server.url("/").toString(), SeerrAuth.ApiKey("k3y"))

    private class Frame(
        val states: CombinedLoadStates,
        val count: Int,
        val refresh: ListRefresh?,
    )

    /** The phases a screen would show, in order and with repeats collapsed, until the refresh settles on [last]. */
    private fun phasesUntil(
        last: PagedPhase,
        cached: List<Int> = emptyList(),
    ): List<PagedPhase> =
        runBlocking {
            val query = RequestListQuery("all", "added", requestedBy = null)
            val store = RoomRequestStore(cache.db)
            if (cached.isNotEmpty()) store.refresh(query.listKey, cached.map { row(query.listKey, it) }, nextSkip = null)
            val refreshes = ListRefreshes<Unit>()
            val mediator =
                RequestsRemoteMediator(
                    query = query,
                    api = ::api,
                    store = store,
                    onRefresh = { refreshes.record(Unit, it) },
                ) { dto, _, key, _ -> row(key, dto.id) }
            val pager = Pager(PagingConfig(pageSize = REQUESTS_PAGE_SIZE), remoteMediator = mediator) { store.pagingSource(query.listKey) }
            val frames = CopyOnWriteArrayList<Frame>()
            val presenter =
                object : PagingDataPresenter<RequestEntity>(mainContext = coroutineContext) {
                    override suspend fun presentPagingDataEvent(event: PagingDataEvent<RequestEntity>) {
                        loadStateFlow.value?.let { frames += Frame(it, size, refreshes.latest.value[Unit]) }
                    }
                }
            val recording =
                launch {
                    presenter.loadStateFlow.filterNotNull().collect { frames += Frame(it, presenter.size, refreshes.latest.value[Unit]) }
                }
            val collecting = launch { pager.flow.collectLatest { presenter.collectFrom(it) } }
            val settled = PagedPhaseTracker()
            withTimeout(TIMEOUT_MILLIS) {
                presenter.loadStateFlow.filterNotNull().first { states ->
                    val refresh = refreshes.latest.value[Unit]
                    refresh != null &&
                        states.mediator?.refresh !is LoadState.Loading &&
                        settled.phase(states, presenter.size, refresh.toPagedRefresh()) == last
                }
            }
            collecting.cancelAndJoin()
            recording.cancelAndJoin()
            val tracker = PagedPhaseTracker()
            frames.map { tracker.phase(it.states, it.count, it.refresh?.toPagedRefresh()) }.fold(emptyList()) { seen, phase ->
                if (seen.lastOrNull() == phase) seen else seen + phase
            }
        }

    @Test
    fun `a cold open of a list with one page goes from the skeleton to the rows and never shows empty`() {
        serve(listOf(1, 2, 3))

        assertEquals(listOf(PagedPhase.Skeleton, rows), phasesUntil(last = rows))
    }

    @Test
    fun `a cold open of a list with more pages goes from the skeleton to the rows`() {
        serve(listOf(1, 2, 3), pages = 2)

        assertEquals(listOf(PagedPhase.Skeleton, rows), phasesUntil(last = rows))
    }

    @Test
    fun `a cold open of a list the server has nothing in goes from the skeleton to empty`() {
        serve(emptyList())

        assertEquals(listOf(PagedPhase.Skeleton, PagedPhase.Empty), phasesUntil(last = PagedPhase.Empty))
    }

    @Test
    fun `an open over cached rows shows them while the refresh runs, then the fresh ones`() {
        serve(listOf(1, 2, 3))

        val phases = phasesUntil(last = rows, cached = listOf(7))

        // The skeleton is the cache's own first read, before it has any rows to show.
        assertEquals(listOf(PagedPhase.Skeleton, refreshingRows, rows), phases)
    }
}

private fun row(
    listKey: String,
    id: Int,
) = RequestEntity(
    listKey = listKey,
    id = id,
    tmdbId = id,
    mediaType = "MOVIE",
    title = null,
    posterUrl = null,
    year = null,
    requestedBy = null,
    requestedById = null,
    requestedAtMillis = null,
    status = 1,
    mediaStatus = null,
    downloadFraction = null,
    downloadEtaMinutes = null,
    downloading = false,
    seasonNumbers = "",
    is4k = false,
    orderIndex = id,
)
