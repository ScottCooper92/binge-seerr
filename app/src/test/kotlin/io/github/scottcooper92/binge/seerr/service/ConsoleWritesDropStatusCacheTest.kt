package io.github.scottcooper92.binge.seerr.service

import androidx.lifecycle.ViewModelStore
import com.binge.companion.contracts.request.v1.Availability
import com.binge.companion.contracts.request.v1.GetStatusRequest
import com.binge.companion.contracts.request.v1.RequestStatus
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.auth.SeerrConnectionHealthMonitor
import io.github.scottcooper92.binge.seerr.data.CachedStatus
import io.github.scottcooper92.binge.seerr.data.MediaStatusStore
import io.github.scottcooper92.binge.seerr.di.dropStatusesOnWrite
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.blocklist.BlocklistDetailEvent
import io.github.scottcooper92.binge.seerr.ui.blocklist.BlocklistDetailViewModel
import io.github.scottcooper92.binge.seerr.ui.blocklist.BlocklistItem
import io.github.scottcooper92.binge.seerr.ui.requests.ModerationEvent
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestModeration
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import io.github.scottcooper92.binge.seerr.util.awaitEvent
import io.github.scottcooper92.binge.seerr.util.requestItem
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.ConcurrentHashMap

private const val TMDB_ID = 100

/**
 * #701: the console's writes drop the exported Service's cached statuses too, not only the Service's own.
 *
 * Nothing in the console names the cache. It does not have to: every console write leaves through the
 * same cached client the Service uses, and that client reports each accepted write (`SeerrWriteInterceptor`).
 * `AuthModule` turns the report into `MediaStatusStore.clearAll()`. These tests build the same wiring by
 * hand, then drive the console's own classes against it.
 */
class ConsoleWritesDropStatusCacheTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()
    private val statuses = InMemoryStatusStore()

    private val movie: MediaId =
        MediaId
            .newBuilder()
            .setMediaType(MediaType.MEDIA_TYPE_MOVIE)
            .setTmdbId(TMDB_ID)
            .build()

    @Before
    fun setUp() {
        seerr.start()
        seerr.viewer(id = 1, permissions = ADMIN)
        // The title is no longer blocked on the server: no media record at all.
        seerr.serve("GET /api/v1/movie/$TMDB_ID", """{"title":"Heat"}""")
        seerr.serve("DELETE /api/v1/blocklist/$TMDB_ID", "", code = 204)
        seerr.serve("POST /api/v1/request/11/approve")
        seerr.serve("POST /api/v1/request/11/decline")
        seerr.serve("DELETE /api/v1/request/11", "", code = 204)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    /**
     * The connection over `AuthModule`'s own write hook, so that is what is under test. The factory is built here rather
     * than by `AuthModule.apiFactory` only so its clients run on the server's drain, which `close()` waits for (#903).
     */
    private suspend fun TestScope.connection(): SeerrConnection =
        seerr.connection(
            this,
            apis =
                SeerrApiFactory(
                    logRequests = false,
                    health = SeerrConnectionHealthMonitor(),
                    onWrite = dropStatusesOnWrite(statuses, backgroundScope),
                    testDispatcher = seerr::newDispatcher,
                ),
        )

    private fun blocklisted() {
        statuses.rows[movie.tmdbId] =
            CachedStatus(RequestStatus.newBuilder().setAvailability(Availability.AVAILABILITY_BLOCKLISTED).build(), fetchedAtMillis = 0L)
    }

    @Test
    fun `after an unblock in the console, the service asks the server rather than answering blocklisted from its cache`() =
        runTest {
            blocklisted()
            val connection = connection()
            val service = SeerrRequestService(connection, versionName = "test", clock = { 0L }, statusCache = statuses)
            val request = GetStatusRequest.newBuilder().setMedia(movie).build()
            assertEquals(Availability.AVAILABILITY_BLOCKLISTED, service.getStatus(request).status.availability)
            assertEquals(0, seerr.count("GET", "/api/v1/movie/$TMDB_ID"))

            val vm = BlocklistDetailViewModel(connection, mainDispatcherRule.dispatcher, blocklistItem, canManage = true)
            viewModels.put("blocklist", vm)
            val removed = awaitEvent(vm.events)
            vm.unblock()
            assertEquals(BlocklistDetailEvent.Removed, removed.await())
            advanceUntilIdle()

            // Only the server can say NOT_REQUESTED: the cached row said BLOCKLISTED.
            assertEquals(Availability.AVAILABILITY_NOT_REQUESTED, service.getStatus(request).status.availability)
        }

    @Test
    fun `approving, declining and deleting a request in the console each drop the cached statuses`() =
        runTest {
            val moderation =
                RequestModeration(
                    scope = backgroundScope,
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                    connection = connection(),
                ) {}
            val writes =
                listOf<Pair<ModerationEvent, () -> Unit>>(
                    ModerationEvent.Approved to { moderation.approve(11) },
                    ModerationEvent.Declined to { moderation.decline(requestItem, blockTitle = false) },
                    ModerationEvent.Removed to { moderation.remove(requestItem, blockTitle = false) },
                )

            for ((expected, write) in writes) {
                blocklisted()
                val event = awaitEvent(moderation.events)
                write()
                assertEquals(expected, event.await())
                advanceUntilIdle()
                assertEquals("$expected left the cache", emptyMap<Int, CachedStatus>(), statuses.rows.toMap())
            }
        }

    private val blocklistItem =
        BlocklistItem(
            id = 1,
            tmdbId = TMDB_ID,
            mediaType = RequestMediaType.Movie,
            title = "Heat",
            posterUrl = null,
            year = "1995",
            addedBy = "Ada",
            addedAtMillis = 0L,
            tags = emptyList(),
        )

    private val requestItem =
        requestItem(id = 11, tmdbId = TMDB_ID, requestedBy = "ada", requestedById = 7, status = SeerrRequestStatusCode(1))

    /** Thread-safe, since the write hook fires from OkHttp's thread. Movies only, keyed by TMDB id. */
    private class InMemoryStatusStore : MediaStatusStore {
        val rows = ConcurrentHashMap<Int, CachedStatus>()

        override suspend fun find(media: MediaId): CachedStatus? = rows[media.tmdbId]

        override suspend fun put(
            media: MediaId,
            cached: CachedStatus,
        ) {
            rows[media.tmdbId] = cached
        }

        override suspend fun clearAll() = rows.clear()
    }
}
