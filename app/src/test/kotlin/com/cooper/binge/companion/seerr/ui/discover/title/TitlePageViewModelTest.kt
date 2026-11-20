package com.cooper.binge.companion.seerr.ui.discover.title

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import com.cooper.binge.companion.seerr.discover.title.TitleCollection
import com.cooper.binge.companion.seerr.discover.title.TitleDetailsRepository
import com.cooper.binge.companion.seerr.discover.title.TitleExtras
import com.cooper.binge.companion.seerr.discover.title.TitleRatings
import com.cooper.binge.companion.seerr.discover.title.TitleRequest
import com.cooper.binge.companion.seerr.discover.title.VersionState
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.RecordingAnalytics
import com.cooper.binge.companion.seerr.util.awaitEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

internal fun movieFixture(
    standard: VersionState = VersionState(TitleAvailability.None, mayRequest = true),
    fourK: VersionState? = null,
) = MovieDetails(
    tmdbId = 603,
    title = "The Matrix",
    tagline = null,
    overview = "A hacker.",
    posterUrl = null,
    backdropUrl = null,
    year = "1999",
    runtimeMinutes = 136,
    certification = "R",
    voteAverage = 8.2,
    voteCount = 100,
    genres = listOf("Action"),
    keywords = emptyList(),
    status = "Released",
    originalLanguage = "en",
    spokenLanguageCodes = emptyList(),
    studios = emptyList(),
    budget = null,
    revenue = null,
    cast = emptyList(),
    keyCrew = emptyList(),
    otherCrew = emptyList(),
    videos = emptyList(),
    providers = null,
    collection = null,
    standard = standard,
    fourK = fourK,
    mediaId = null,
    webUrl = "https://seerr.example/movie/603",
)

private class FakeTitles(
    var movie: MovieDetails = movieFixture(),
    var failMovie: Throwable? = null,
    var extras: TitleExtras = TitleExtras(TitleRatings(90, null, null), emptyList(), emptyList()),
    var collection: List<TitleResult> = emptyList(),
    var failCollection: Throwable? = null,
) : TitleDetailsRepository {
    var movieReads = 0
    var failUnblock: Throwable? = null
    val unblocked = mutableListOf<Int>()
    var failBlock: Throwable? = null
    val blocked = mutableListOf<Pair<Int, String>>()
    var failWatchlist: Throwable? = null
    val watchlistCalls = mutableListOf<Triple<Int, String, Boolean>>()
    val collectionReads = mutableListOf<Int>()

    override suspend fun collectionParts(collectionId: Int): List<TitleResult> {
        collectionReads += collectionId
        return failCollection?.let { throw it } ?: collection
    }

    override suspend fun setWatchlist(
        tmdbId: Int,
        title: String,
        onList: Boolean,
    ) {
        failWatchlist?.let { throw it }
        watchlistCalls += Triple(tmdbId, title, onList)
    }

    override suspend fun serverProfile() = null

    override suspend fun block(
        tmdbId: Int,
        title: String,
    ) {
        failBlock?.let { throw it }
        blocked += tmdbId to title
    }

    override suspend fun unblock(tmdbId: Int) {
        failUnblock?.let { throw it }
        unblocked += tmdbId
    }

    override suspend fun movie(tmdbId: Int): MovieDetails {
        movieReads++
        return failMovie?.let { throw it } ?: movie
    }

    override suspend fun extras(
        kind: TitleKind,
        tmdbId: Int,
    ) = extras
}

class TitlePageViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TitlePageViewModel.ready() = uiState.value as TitlePageUiState.Ready

    @Test
    fun `the page loads the film then its extras`() {
        val vm = TitlePageViewModel(FakeTitles(), 603)

        assertEquals("The Matrix", vm.ready().movie.title)
        assertNotNull(vm.ready().extras)
    }

    @Test
    fun `a title with nothing of the viewer's to open still opens the status sheet`() {
        val imported = movieFixture(standard = VersionState(TitleAvailability.Available, mayRequest = false))
        val vm = TitlePageViewModel(FakeTitles(movie = imported), 603)

        vm.onPrimary()
        assertTrue(vm.ready().statusSheetOpen)
        assertFalse(vm.ready().requestSheetOpen)

        vm.closeSheet(TitleSheet.Status)
        assertFalse(vm.ready().statusSheetOpen)
    }

    @Test
    fun `the report sheet opens and closes`() {
        val vm = TitlePageViewModel(FakeTitles(), 603)

        vm.setReportSheetOpen(true)
        assertTrue(vm.ready().reportSheetOpen)

        vm.setReportSheetOpen(false)
        assertFalse(vm.ready().reportSheetOpen)
    }

    @Test
    fun `a failed load is an error that retries`() {
        val titles = FakeTitles(failMovie = IOException("down"))
        val vm = TitlePageViewModel(titles, 603)
        assertTrue(vm.uiState.value is TitlePageUiState.Error)

        titles.failMovie = null
        vm.load()

        assertTrue(vm.uiState.value is TitlePageUiState.Ready)
    }

    @Test
    fun `the request sheet opens only while the title can be requested`() {
        val none = movieFixture(standard = VersionState(TitleAvailability.Available, mayRequest = false))
        val vm = TitlePageViewModel(FakeTitles(movie = none), 603)
        vm.onPrimary()
        assertFalse(vm.ready().requestSheetOpen)

        val open = TitlePageViewModel(FakeTitles(), 603)
        open.onPrimary()
        assertTrue(open.ready().requestSheetOpen)
    }

    @Test
    fun `a held title opens its request and keeps it after the sheet closes`() {
        val held =
            movieFixture(
                standard = VersionState(TitleAvailability.Pending, mayRequest = false),
            ).copy(requests = listOf(TitleRequest(7, false, SeerrRequestStatusCode.Pending, 5)))
        val vm = TitlePageViewModel(FakeTitles(movie = held), 603)

        vm.onPrimary()
        assertEquals(7, vm.ready().manageRequestId)
        assertTrue(vm.ready().manageSheetOpen)

        vm.closeSheet(TitleSheet.Manage)
        assertFalse(vm.ready().manageSheetOpen)
        assertEquals("the id stays so a moderation's result still has its sheet", 7, vm.ready().manageRequestId)
    }

    @Test
    fun `a 4K version alone is enough to offer the request`() {
        val movie = movieFixture(VersionState(TitleAvailability.Available, false), VersionState(TitleAvailability.None, true))

        assertTrue(TitlePageViewModel(FakeTitles(movie = movie), 603).ready().mayRequest)
    }

    @Test
    fun `Request in 4K from a request's sheet swaps it for the request sheet`() {
        val held =
            movieFixture(
                standard = VersionState(TitleAvailability.Pending, mayRequest = false),
                fourK = VersionState(TitleAvailability.None, mayRequest = true),
            ).copy(requests = listOf(TitleRequest(7, false, SeerrRequestStatusCode.Pending, 5)))
        val vm = TitlePageViewModel(FakeTitles(movie = held), 603)
        vm.onPrimary()

        vm.requestFromSheet(true)

        assertFalse(vm.ready().manageSheetOpen)
        assertTrue(vm.ready().requestSheetOpen)
        assertTrue(vm.ready().requestSheetFourK)
        assertEquals(7, vm.ready().manageRequestId)
    }

    @Test
    fun `an available title with 4K open opens the status sheet, and Request in 4K leaves it for the request sheet`() {
        val available =
            movieFixture(
                standard = VersionState(TitleAvailability.Available, mayRequest = false),
                fourK = VersionState(TitleAvailability.None, mayRequest = true),
            )
        val vm = TitlePageViewModel(FakeTitles(movie = available), 603)

        vm.onPrimary()

        assertTrue(vm.ready().statusSheetOpen)
        assertFalse(vm.ready().requestSheetOpen)

        vm.requestFromSheet(true)

        assertFalse(vm.ready().statusSheetOpen)
        assertTrue(vm.ready().requestSheetOpen)
        assertTrue(vm.ready().requestSheetFourK)
    }

    @Test
    fun `Request in 4K starts on 4K even while the standard version is still open`() {
        val held =
            movieFixture(
                standard = VersionState(TitleAvailability.Pending, mayRequest = true),
                fourK = VersionState(TitleAvailability.None, mayRequest = true),
            ).copy(requests = listOf(TitleRequest(7, false, SeerrRequestStatusCode.Pending, 5)))
        val vm = TitlePageViewModel(FakeTitles(movie = held), 603)
        vm.onPrimary()
        assertFalse(vm.ready().requestSheetFourK)

        vm.requestFromSheet(true)

        assertTrue(vm.ready().requestSheetFourK)
    }

    @Test
    fun `a request that went through closes the sheet says so and reads the title again`() =
        runTest {
            val titles = FakeTitles()
            val vm = TitlePageViewModel(titles, 603)
            vm.onPrimary()
            titles.movie = movieFixture(standard = VersionState(TitleAvailability.Pending, mayRequest = false))

            val event = awaitEvent(vm.events)
            vm.requested(RequestResult.Created)

            assertEquals(TitlePageEvent.Requested(RequestResult.Created), event.await())
            assertFalse(vm.ready().requestSheetOpen)
            assertEquals(
                TitleAvailability.Pending,
                vm
                    .ready()
                    .movie.standard.availability,
            )
        }

    @Test
    fun `unblocking takes a blocklisted title off the list reads it again and says so`() =
        runTest {
            val repo = FakeTitles()
            repo.movie = movieFixture(standard = VersionState(TitleAvailability.Blocklisted, mayRequest = false)).copy(mayUnblock = true)
            val vm = TitlePageViewModel(repo, 603)
            vm.onPrimary()
            assertTrue(vm.ready().statusSheetOpen)
            repo.movie = movieFixture()

            val event = awaitEvent(vm.events)
            vm.blocklist(BlocklistStep.Unblock)

            assertEquals(TitlePageEvent.Unblocked, event.await())
            assertEquals(listOf(603), repo.unblocked)
            assertFalse(vm.ready().statusSheetOpen)
            assertEquals(
                TitleAvailability.None,
                vm
                    .ready()
                    .movie.standard.availability,
            )
        }

    @Test
    fun `a successful unblock is counted as a blocklist change`() =
        runTest {
            val repo = FakeTitles()
            repo.movie = movieFixture(standard = VersionState(TitleAvailability.Blocklisted, mayRequest = false)).copy(mayUnblock = true)
            val analytics = RecordingAnalytics()
            val vm = TitlePageViewModel(repo, 603, analytics)
            val event = awaitEvent(vm.events)

            vm.blocklist(BlocklistStep.Unblock)
            event.await()

            assertEquals(
                listOf(AnalyticsEvents.BLOCKLIST_CHANGED to mapOf<String, Any>(AnalyticsEvents.PARAM_ACTION to "movie_unblocked")),
                analytics.events,
            )
        }

    @Test
    fun `a refused unblock the server answers with an error is reported as a failed operation`() =
        runTest {
            val repo = FakeTitles()
            repo.movie = movieFixture(standard = VersionState(TitleAvailability.Blocklisted, mayRequest = false)).copy(mayUnblock = true)
            repo.failUnblock = HttpException(Response.error<Unit>(500, "".toResponseBody()))
            val analytics = RecordingAnalytics()
            val vm = TitlePageViewModel(repo, 603, analytics)
            val event = awaitEvent(vm.events)

            vm.blocklist(BlocklistStep.Unblock)
            event.await()

            assertEquals("unblock_title", analytics.events.single().second[AnalyticsEvents.PARAM_OPERATION])
        }

    @Test
    fun `a refused unblock is not counted`() =
        runTest {
            val repo = FakeTitles()
            repo.movie = movieFixture(standard = VersionState(TitleAvailability.Blocklisted, mayRequest = false)).copy(mayUnblock = true)
            repo.failUnblock = IOException("down")
            val analytics = RecordingAnalytics()
            val vm = TitlePageViewModel(repo, 603, analytics)
            val event = awaitEvent(vm.events)

            vm.blocklist(BlocklistStep.Unblock)
            event.await()

            assertTrue(analytics.events.isEmpty())
        }

    @Test
    fun `blocking asks first then blocks reads the title again and says so`() =
        runTest {
            val repo = FakeTitles()
            repo.movie = movieFixture().copy(mayBlock = true)
            val analytics = RecordingAnalytics()
            val vm = TitlePageViewModel(repo, 603, analytics)
            vm.blocklist(BlocklistStep.Ask)
            assertTrue(vm.ready().blockConfirmOpen)
            repo.movie = movieFixture(standard = VersionState(TitleAvailability.Blocklisted, mayRequest = false))

            val event = awaitEvent(vm.events)
            vm.blocklist(BlocklistStep.Block)

            assertEquals(TitlePageEvent.Blocked, event.await())
            assertEquals(listOf(603 to vm.ready().movie.title), repo.blocked)
            assertFalse(vm.ready().blockConfirmOpen)
            assertEquals(
                TitleAvailability.Blocklisted,
                vm
                    .ready()
                    .movie.standard.availability,
            )
            assertEquals(
                listOf(AnalyticsEvents.BLOCKLIST_CHANGED to mapOf<String, Any>(AnalyticsEvents.PARAM_ACTION to "movie_blocked")),
                analytics.events,
            )
        }

    @Test
    fun `cancelling the confirm blocks nothing`() {
        val repo = FakeTitles()
        repo.movie = movieFixture().copy(mayBlock = true)
        val vm = TitlePageViewModel(repo, 603)

        vm.blocklist(BlocklistStep.Ask)
        vm.blocklist(BlocklistStep.Cancel)

        assertFalse(vm.ready().blockConfirmOpen)
        assertTrue(repo.blocked.isEmpty())
    }

    @Test
    fun `a viewer who may not block never reaches the server`() {
        val repo = FakeTitles()
        repo.movie = movieFixture()
        val vm = TitlePageViewModel(repo, 603)

        vm.blocklist(BlocklistStep.Block)

        assertTrue(repo.blocked.isEmpty())
    }

    @Test
    fun `a refused block says so and leaves the title as it was`() =
        runTest {
            val repo = FakeTitles()
            repo.movie = movieFixture().copy(mayBlock = true)
            repo.failBlock = IOException("down")
            val analytics = RecordingAnalytics()
            val vm = TitlePageViewModel(repo, 603, analytics)

            val event = awaitEvent(vm.events)
            vm.blocklist(BlocklistStep.Block)

            assertTrue(event.await() is TitlePageEvent.BlockFailed)
            assertEquals(
                TitleAvailability.None,
                vm
                    .ready()
                    .movie.standard.availability,
            )
            assertTrue(analytics.events.none { it.first == AnalyticsEvents.BLOCKLIST_CHANGED })
        }

    @Test
    fun `a viewer who may not unblock never reaches the server`() {
        val repo = FakeTitles()
        repo.movie = movieFixture(standard = VersionState(TitleAvailability.Blocklisted, mayRequest = false))
        val vm = TitlePageViewModel(repo, 603)

        vm.blocklist(BlocklistStep.Unblock)

        assertTrue(repo.unblocked.isEmpty())
    }

    @Test
    fun `a refused unblock says so and leaves the title as it was`() =
        runTest {
            val repo = FakeTitles()
            repo.movie = movieFixture(standard = VersionState(TitleAvailability.Blocklisted, mayRequest = false)).copy(mayUnblock = true)
            repo.failUnblock = IOException("down")
            val vm = TitlePageViewModel(repo, 603)

            val event = awaitEvent(vm.events)
            vm.blocklist(BlocklistStep.Unblock)

            assertTrue(event.await() is TitlePageEvent.UnblockFailed)
            assertEquals(
                TitleAvailability.Blocklisted,
                vm
                    .ready()
                    .movie.standard.availability,
            )
        }

    @Test
    fun `the watchlist button flips at once and the server is told`() =
        runTest {
            val repo = FakeTitles()
            repo.movie = movieFixture().copy(mayWatchlist = true)
            val vm = TitlePageViewModel(repo, 603)

            vm.toggleWatchlist()
            assertTrue(vm.ready().movie.onWatchlist)
            vm.toggleWatchlist()
            assertFalse(vm.ready().movie.onWatchlist)

            assertEquals(listOf(Triple(603, "The Matrix", true), Triple(603, "The Matrix", false)), repo.watchlistCalls)
            assertFalse(vm.ready().watchlistBusy)
        }

    @Test
    fun `a refused watchlist call puts the button back and says so`() =
        runTest {
            val repo = FakeTitles()
            repo.movie = movieFixture().copy(mayWatchlist = true)
            repo.failWatchlist = IOException("down")
            val vm = TitlePageViewModel(repo, 603)

            val event = awaitEvent(vm.events)
            vm.toggleWatchlist()

            assertTrue(event.await() is TitlePageEvent.WatchlistFailed)
            assertFalse(vm.ready().movie.onWatchlist)
            assertFalse(vm.ready().watchlistBusy)
        }

    @Test
    fun `a title on the watchlist is taken off it`() =
        runTest {
            val repo = FakeTitles()
            repo.movie = movieFixture().copy(mayWatchlist = true, onWatchlist = true)
            val vm = TitlePageViewModel(repo, 603)

            vm.toggleWatchlist()

            assertFalse(vm.ready().movie.onWatchlist)
            assertEquals(listOf(Triple(603, "The Matrix", false)), repo.watchlistCalls)
        }

    @Test
    fun `a server without a watchlist never gets a call`() =
        runTest {
            val repo = FakeTitles()
            repo.movie = movieFixture()
            val vm = TitlePageViewModel(repo, 603)

            vm.toggleWatchlist()

            assertTrue(repo.watchlistCalls.isEmpty())
            assertFalse(vm.ready().movie.onWatchlist)
        }

    @Test
    fun `a failed read after a request keeps the page and says so`() =
        runTest {
            val titles = FakeTitles()
            val vm = TitlePageViewModel(titles, 603)
            vm.onPrimary()
            titles.failMovie = IOException("down")
            val events = mutableListOf<TitlePageEvent>()
            val collecting = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }

            vm.requested(RequestResult.Created)
            runCurrent()

            assertEquals(listOf(TitlePageEvent.Requested(RequestResult.Created), TitlePageEvent.ReloadFailed), events)
            assertTrue(vm.uiState.value is TitlePageUiState.Ready)
            collecting.cancel()
        }

    @Test
    fun `a reload after a failed one keeps the page and updates it`() =
        runTest {
            val titles = FakeTitles()
            val vm = TitlePageViewModel(titles, 603)
            titles.failMovie = IOException("down")
            vm.reload()
            assertTrue(vm.uiState.value is TitlePageUiState.Ready)

            titles.failMovie = null
            titles.movie = movieFixture(standard = VersionState(TitleAvailability.Pending, mayRequest = false))
            vm.reload()

            assertTrue(vm.uiState.value is TitlePageUiState.Ready)
            assertEquals(
                TitleAvailability.Pending,
                vm
                    .ready()
                    .movie.standard.availability,
            )
        }

    private fun film(id: Int) = TitleResult(TitleKind.Movie, id, "Film $id", "2003", null, null, null, null, TitleAvailability.None, null)

    private val inCollection = movieFixture().copy(collection = TitleCollection(2344, "The Matrix Collection", null))

    @Test
    fun `a film in a collection loads the collection's other films and leaves itself out`() {
        val titles = FakeTitles(movie = inCollection, collection = listOf(film(603), film(604), film(605)))
        val vm = TitlePageViewModel(titles, 603)

        assertEquals(listOf(2344), titles.collectionReads)
        assertEquals(listOf(604, 605), vm.ready().collectionParts.map { it.tmdbId })
    }

    @Test
    fun `a film with no collection reads none`() {
        val titles = FakeTitles()
        val vm = TitlePageViewModel(titles, 603)

        assertTrue(titles.collectionReads.isEmpty())
        assertTrue(vm.ready().collectionParts.isEmpty())
    }

    @Test
    fun `a collection that fails to read leaves the rail out and the page open`() {
        val titles = FakeTitles(movie = inCollection, failCollection = IOException("down"))
        val vm = TitlePageViewModel(titles, 603)

        assertTrue(vm.ready().collectionParts.isEmpty())
        assertEquals("The Matrix", vm.ready().movie.title)
    }
}
