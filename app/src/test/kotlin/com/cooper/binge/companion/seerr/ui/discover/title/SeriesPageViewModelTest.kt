package com.cooper.binge.companion.seerr.ui.discover.title

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.title.NextEpisode
import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.discover.title.SeasonInfo
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.discover.title.SeriesDetailsRepository
import com.cooper.binge.companion.seerr.discover.title.TitleCompany
import com.cooper.binge.companion.seerr.discover.title.TitleExtras
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
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

internal fun seasonFixture(
    number: Int,
    standard: TitleAvailability = TitleAvailability.None,
) = SeasonInfo(number, "Season $number", episodeCount = 10, airYear = "${2010 + number}", standard = standard, fourK = null)

internal fun seriesFixture(
    seasons: List<SeasonInfo> = listOf(seasonFixture(1), seasonFixture(2)),
    standard: VersionState = VersionState(TitleAvailability.None, mayRequest = true),
    fourK: VersionState? = null,
) = SeriesDetails(
    tmdbId = 1399,
    name = "A Show",
    tagline = null,
    overview = "A story.",
    posterUrl = null,
    backdropUrl = null,
    firstAirYear = "2011",
    firstAirDate = "2011-04-17",
    lastAirDate = null,
    episodeRuntimeMinutes = 55,
    certification = "TV-MA",
    voteAverage = 8.4,
    voteCount = 100,
    genres = listOf("Drama"),
    keywords = emptyList(),
    status = "Returning Series",
    inProduction = true,
    originalLanguage = "en",
    spokenLanguageCodes = emptyList(),
    networks = listOf(TitleCompany(1, "A Network")),
    creators = listOf(TitlePerson(1, "A Creator", null, null)),
    seasonCount = seasons.size,
    episodeCount = seasons.sumOf { it.episodeCount },
    nextEpisode = NextEpisode(seasons.size, 1, "The Next One", "2099-01-01"),
    seasons = seasons,
    cast = emptyList(),
    keyCrew = emptyList(),
    otherCrew = emptyList(),
    videos = emptyList(),
    providers = null,
    standard = standard,
    fourK = fourK,
    partialRequests = true,
    mediaId = null,
    webUrl = "https://seerr.example/tv/1399",
)

private class FakeSeries(
    var series: SeriesDetails = seriesFixture(),
    var failSeries: Throwable? = null,
    var extras: TitleExtras = TitleExtras(TitleRatings(90, null, null), emptyList(), emptyList()),
    var failSeasons: Throwable? = null,
) : SeriesDetailsRepository {
    val seasonReads = mutableListOf<Int>()
    var failUnblock: Throwable? = null
    val unblocked = mutableListOf<Int>()
    var failBlock: Throwable? = null
    val blocked = mutableListOf<Pair<Int, String>>()
    var failWatchlist: Throwable? = null
    val watchlistCalls = mutableListOf<Triple<Int, String, Boolean>>()

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

    override suspend fun season(
        tmdbId: Int,
        seasonNumber: Int,
    ): List<SeasonEpisode> {
        seasonReads += seasonNumber
        failSeasons?.let { throw it }
        return listOf(SeasonEpisode(1, "Pilot $seasonNumber", null, "2011-04-17", null, null))
    }

    override suspend fun series(tmdbId: Int): SeriesDetails = failSeries?.let { throw it } ?: series

    override suspend fun extras(tmdbId: Int) = extras
}

class SeriesPageViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun SeriesPageViewModel.ready() = uiState.value as SeriesPageUiState.Ready

    @Test
    fun `the page loads the show then its extras`() {
        val vm = SeriesPageViewModel(FakeSeries(), 1399)

        assertEquals("A Show", vm.ready().series.name)
        assertNotNull(vm.ready().extras)
    }

    @Test
    fun `a series with nothing of the viewer's to open still opens the status sheet`() {
        val imported = seriesFixture(standard = VersionState(TitleAvailability.Available, mayRequest = false))
        val vm = SeriesPageViewModel(FakeSeries(series = imported), 1399)

        vm.onPrimary()
        assertTrue(vm.ready().statusSheetOpen)

        vm.closeSheet(TitleSheet.Status)
        assertFalse(vm.ready().statusSheetOpen)
    }

    @Test
    fun `the report sheet opens and closes`() {
        val vm = SeriesPageViewModel(FakeSeries(), 1399)

        vm.setReportSheetOpen(true)
        assertTrue(vm.ready().reportSheetOpen)

        vm.setReportSheetOpen(false)
        assertFalse(vm.ready().reportSheetOpen)
    }

    @Test
    fun `a failed load is an error that retries`() {
        val repository = FakeSeries(failSeries = IOException("down"))
        val vm = SeriesPageViewModel(repository, 1399)
        assertTrue(vm.uiState.value is SeriesPageUiState.Error)

        repository.failSeries = null
        vm.load()

        assertTrue(vm.uiState.value is SeriesPageUiState.Ready)
    }

    @Test
    fun `the request sheet opens only while a season can still be asked for`() {
        val covered = seriesFixture(standard = VersionState(TitleAvailability.Available, mayRequest = false))
        val closed = SeriesPageViewModel(FakeSeries(series = covered), 1399)
        closed.onPrimary()
        assertFalse(closed.ready().requestSheetOpen)

        val open = SeriesPageViewModel(FakeSeries(), 1399)
        open.onPrimary()
        assertTrue(open.ready().requestSheetOpen)
    }

    @Test
    fun `a held series opens its request and keeps it after the sheet closes`() {
        val held =
            seriesFixture(
                standard = VersionState(TitleAvailability.Pending, mayRequest = false),
            ).copy(requests = listOf(TitleRequest(7, false, SeerrRequestStatusCode.Pending, 5)))
        val vm = SeriesPageViewModel(FakeSeries(series = held), 1399)

        vm.onPrimary()
        assertEquals(7, vm.ready().manageRequestId)
        assertTrue(vm.ready().manageSheetOpen)

        vm.closeSheet(TitleSheet.Manage)
        assertFalse(vm.ready().manageSheetOpen)
        assertEquals(7, vm.ready().manageRequestId)
    }

    @Test
    fun `Request in 4K from a request's sheet swaps it for the request sheet`() {
        val held =
            seriesFixture(
                standard = VersionState(TitleAvailability.Pending, mayRequest = false),
                fourK = VersionState(TitleAvailability.None, mayRequest = true),
            ).copy(requests = listOf(TitleRequest(7, false, SeerrRequestStatusCode.Pending, 5)))
        val vm = SeriesPageViewModel(FakeSeries(series = held), 1399)
        vm.onPrimary()

        vm.requestFromSheet(true)

        assertFalse(vm.ready().manageSheetOpen)
        assertTrue(vm.ready().requestSheetOpen)
        assertTrue(vm.ready().requestSheetFourK)
        assertEquals(7, vm.ready().manageRequestId)
    }

    @Test
    fun `Request in 4K starts on 4K even while the standard version is still open`() {
        val held =
            seriesFixture(
                standard = VersionState(TitleAvailability.Pending, mayRequest = true),
                fourK = VersionState(TitleAvailability.None, mayRequest = true),
            ).copy(requests = listOf(TitleRequest(7, false, SeerrRequestStatusCode.Pending, 5)))
        val vm = SeriesPageViewModel(FakeSeries(series = held), 1399)
        vm.onPrimary()
        assertFalse(vm.ready().requestSheetFourK)

        vm.requestFromSheet(true)

        assertTrue(vm.ready().requestSheetFourK)
    }

    @Test
    fun `a partly available series with seasons left opens the status sheet, and its remaining seasons open the request sheet`() {
        val partly = seriesFixture(standard = VersionState(TitleAvailability.PartiallyAvailable, mayRequest = true))
        val vm = SeriesPageViewModel(FakeSeries(series = partly), 1399)

        vm.onPrimary()

        assertTrue(vm.ready().statusSheetOpen)
        assertFalse(vm.ready().requestSheetOpen)

        vm.requestFromSheet(false)

        assertFalse(vm.ready().statusSheetOpen)
        assertTrue(vm.ready().requestSheetOpen)
        assertFalse(vm.ready().requestSheetFourK)
    }

    @Test
    fun `unblocking takes a blocklisted title off the list reads it again and says so`() =
        runTest {
            val repo = FakeSeries()
            repo.series = seriesFixture(standard = VersionState(TitleAvailability.Blocklisted, mayRequest = false)).copy(mayUnblock = true)
            val vm = SeriesPageViewModel(repo, 1399)
            vm.onPrimary()
            assertTrue(vm.ready().statusSheetOpen)
            repo.series = seriesFixture()

            val event = awaitEvent(vm.events)
            vm.blocklist(BlocklistStep.Unblock)

            assertEquals(SeriesPageEvent.Unblocked, event.await())
            assertEquals(listOf(1399), repo.unblocked)
            assertFalse(vm.ready().statusSheetOpen)
            assertEquals(
                TitleAvailability.None,
                vm
                    .ready()
                    .series.standard.availability,
            )
        }

    @Test
    fun `blocking asks first then blocks reads the show again and says so`() =
        runTest {
            val repo = FakeSeries()
            repo.series = seriesFixture().copy(mayBlock = true)
            val analytics = RecordingAnalytics()
            val vm = SeriesPageViewModel(repo, 1399, analytics)
            vm.blocklist(BlocklistStep.Ask)
            assertTrue(vm.ready().blockConfirmOpen)
            repo.series = seriesFixture(standard = VersionState(TitleAvailability.Blocklisted, mayRequest = false))

            val event = awaitEvent(vm.events)
            vm.blocklist(BlocklistStep.Block)

            assertEquals(SeriesPageEvent.Blocked, event.await())
            assertEquals(listOf(1399 to vm.ready().series.name), repo.blocked)
            assertFalse(vm.ready().blockConfirmOpen)
            assertEquals(
                TitleAvailability.Blocklisted,
                vm
                    .ready()
                    .series.standard.availability,
            )
            assertEquals(
                listOf(AnalyticsEvents.BLOCKLIST_CHANGED to mapOf<String, Any>(AnalyticsEvents.PARAM_ACTION to "series_blocked")),
                analytics.events,
            )
        }

    @Test
    fun `a viewer who may not block a show never reaches the server`() {
        val repo = FakeSeries()
        repo.series = seriesFixture()
        val vm = SeriesPageViewModel(repo, 1399)

        vm.blocklist(BlocklistStep.Block)

        assertTrue(repo.blocked.isEmpty())
    }

    @Test
    fun `a refused block of a show says so and leaves it as it was`() =
        runTest {
            val repo = FakeSeries()
            repo.series = seriesFixture().copy(mayBlock = true)
            repo.failBlock = IOException("down")
            val vm = SeriesPageViewModel(repo, 1399)

            val event = awaitEvent(vm.events)
            vm.blocklist(BlocklistStep.Block)

            assertTrue(event.await() is SeriesPageEvent.BlockFailed)
            assertEquals(
                TitleAvailability.None,
                vm
                    .ready()
                    .series.standard.availability,
            )
        }

    @Test
    fun `a successful unblock is counted as a blocklist change`() =
        runTest {
            val repo = FakeSeries()
            repo.series = seriesFixture(standard = VersionState(TitleAvailability.Blocklisted, mayRequest = false)).copy(mayUnblock = true)
            val analytics = RecordingAnalytics()
            val vm = SeriesPageViewModel(repo, 1399, analytics)
            val event = awaitEvent(vm.events)

            vm.blocklist(BlocklistStep.Unblock)
            event.await()

            assertEquals(
                listOf(AnalyticsEvents.BLOCKLIST_CHANGED to mapOf<String, Any>(AnalyticsEvents.PARAM_ACTION to "series_unblocked")),
                analytics.events,
            )
        }

    @Test
    fun `a refused unblock the server answers with an error is reported as a failed operation`() =
        runTest {
            val repo = FakeSeries()
            repo.series = seriesFixture(standard = VersionState(TitleAvailability.Blocklisted, mayRequest = false)).copy(mayUnblock = true)
            repo.failUnblock = HttpException(Response.error<Unit>(500, "".toResponseBody()))
            val analytics = RecordingAnalytics()
            val vm = SeriesPageViewModel(repo, 1399, analytics)
            val event = awaitEvent(vm.events)

            vm.blocklist(BlocklistStep.Unblock)
            event.await()

            assertEquals("unblock_title", analytics.events.single().second[AnalyticsEvents.PARAM_OPERATION])
        }

    @Test
    fun `a refused unblock is not counted`() =
        runTest {
            val repo = FakeSeries()
            repo.series = seriesFixture(standard = VersionState(TitleAvailability.Blocklisted, mayRequest = false)).copy(mayUnblock = true)
            repo.failUnblock = IOException("down")
            val analytics = RecordingAnalytics()
            val vm = SeriesPageViewModel(repo, 1399, analytics)
            val event = awaitEvent(vm.events)

            vm.blocklist(BlocklistStep.Unblock)
            event.await()

            assertTrue(analytics.events.isEmpty())
        }

    @Test
    fun `a viewer who may not unblock never reaches the server`() {
        val repo = FakeSeries()
        repo.series = seriesFixture(standard = VersionState(TitleAvailability.Blocklisted, mayRequest = false))
        val vm = SeriesPageViewModel(repo, 1399)

        vm.blocklist(BlocklistStep.Unblock)

        assertTrue(repo.unblocked.isEmpty())
    }

    @Test
    fun `a refused unblock says so and leaves the title as it was`() =
        runTest {
            val repo = FakeSeries()
            repo.series = seriesFixture(standard = VersionState(TitleAvailability.Blocklisted, mayRequest = false)).copy(mayUnblock = true)
            repo.failUnblock = IOException("down")
            val vm = SeriesPageViewModel(repo, 1399)

            val event = awaitEvent(vm.events)
            vm.blocklist(BlocklistStep.Unblock)

            assertTrue(event.await() is SeriesPageEvent.UnblockFailed)
            assertEquals(
                TitleAvailability.Blocklisted,
                vm
                    .ready()
                    .series.standard.availability,
            )
        }

    @Test
    fun `the watchlist button flips at once and the server is told`() =
        runTest {
            val repo = FakeSeries()
            repo.series = seriesFixture().copy(mayWatchlist = true)
            val vm = SeriesPageViewModel(repo, 1399)

            vm.toggleWatchlist()
            assertTrue(vm.ready().series.onWatchlist)
            vm.toggleWatchlist()
            assertFalse(vm.ready().series.onWatchlist)

            assertEquals(listOf(Triple(1399, "A Show", true), Triple(1399, "A Show", false)), repo.watchlistCalls)
            assertFalse(vm.ready().watchlistBusy)
        }

    @Test
    fun `a refused watchlist call puts the button back and says so`() =
        runTest {
            val repo = FakeSeries()
            repo.series = seriesFixture().copy(mayWatchlist = true)
            repo.failWatchlist = IOException("down")
            val vm = SeriesPageViewModel(repo, 1399)

            val event = awaitEvent(vm.events)
            vm.toggleWatchlist()

            assertTrue(event.await() is SeriesPageEvent.WatchlistFailed)
            assertFalse(vm.ready().series.onWatchlist)
            assertFalse(vm.ready().watchlistBusy)
        }

    @Test
    fun `a title on the watchlist is taken off it`() =
        runTest {
            val repo = FakeSeries()
            repo.series = seriesFixture().copy(mayWatchlist = true, onWatchlist = true)
            val vm = SeriesPageViewModel(repo, 1399)

            vm.toggleWatchlist()

            assertFalse(vm.ready().series.onWatchlist)
            assertEquals(listOf(Triple(1399, "A Show", false)), repo.watchlistCalls)
        }

    @Test
    fun `a server without a watchlist never gets a call`() =
        runTest {
            val repo = FakeSeries()
            repo.series = seriesFixture()
            val vm = SeriesPageViewModel(repo, 1399)

            vm.toggleWatchlist()

            assertTrue(repo.watchlistCalls.isEmpty())
            assertFalse(vm.ready().series.onWatchlist)
        }

    @Test
    fun `a request that went through closes the sheet says so and reads the show again`() =
        runTest {
            val repository = FakeSeries()
            val vm = SeriesPageViewModel(repository, 1399)
            vm.onPrimary()
            repository.series = seriesFixture(seasons = listOf(seasonFixture(1, TitleAvailability.Pending), seasonFixture(2)))

            val event = awaitEvent(vm.events)
            vm.requested(RequestResult.Created)

            assertEquals(SeriesPageEvent.Requested(RequestResult.Created), event.await())
            assertFalse(vm.ready().requestSheetOpen)
            assertEquals(
                TitleAvailability.Pending,
                vm
                    .ready()
                    .series.seasons
                    .first()
                    .standard,
            )
        }

    @Test
    fun `a failed read after a request keeps the page and says so`() =
        runTest {
            val repository = FakeSeries()
            val vm = SeriesPageViewModel(repository, 1399)
            vm.onPrimary()
            repository.failSeries = IOException("down")
            val events = mutableListOf<SeriesPageEvent>()
            val collecting = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }

            vm.requested(RequestResult.Created)
            runCurrent()

            assertEquals(listOf(SeriesPageEvent.Requested(RequestResult.Created), SeriesPageEvent.ReloadFailed), events)
            assertTrue(vm.uiState.value is SeriesPageUiState.Ready)
            collecting.cancel()
        }

    @Test
    fun `a reload after a failed one keeps the page and updates it`() =
        runTest {
            val repository = FakeSeries()
            val vm = SeriesPageViewModel(repository, 1399)
            repository.failSeries = IOException("down")
            vm.reload()
            assertTrue(vm.uiState.value is SeriesPageUiState.Ready)

            repository.failSeries = null
            repository.series = seriesFixture(seasons = listOf(seasonFixture(1, TitleAvailability.Pending)))
            vm.reload()

            assertTrue(vm.uiState.value is SeriesPageUiState.Ready)
            assertEquals(
                TitleAvailability.Pending,
                vm
                    .ready()
                    .series.seasons
                    .first()
                    .standard,
            )
        }

    @Test
    fun `the first season is selected and its episodes read`() {
        val vm = SeriesPageViewModel(FakeSeries(), 1399)

        assertEquals(1, vm.ready().selectedSeason)
        assertEquals("Pilot 1", (vm.ready().episodes.getValue(1) as EpisodesState.Ready).episodes.single().name)
    }

    @Test
    fun `selecting a season reads it once and keeps what it read`() {
        val repository = FakeSeries()
        val vm = SeriesPageViewModel(repository, 1399)

        vm.selectSeason(2)
        vm.selectSeason(1)
        vm.selectSeason(2)

        assertEquals(listOf(1, 2), repository.seasonReads)
        assertEquals(2, vm.ready().selectedSeason)
    }

    @Test
    fun `a season the show does not list cannot be selected`() {
        val vm = SeriesPageViewModel(FakeSeries(), 1399)

        vm.selectSeason(9)

        assertEquals(1, vm.ready().selectedSeason)
    }

    @Test
    fun `a failed season is an error that a retry reads again`() {
        val repository = FakeSeries(failSeasons = IOException("down"))
        val vm = SeriesPageViewModel(repository, 1399)
        assertTrue(vm.ready().episodes.getValue(1) is EpisodesState.Error)

        repository.failSeasons = null
        vm.retryEpisodes()

        assertTrue(vm.ready().episodes.getValue(1) is EpisodesState.Ready)
    }

    @Test
    fun `a show with no seasons selects none`() {
        val vm = SeriesPageViewModel(FakeSeries(series = seriesFixture(seasons = emptyList())), 1399)

        assertEquals(null, vm.ready().selectedSeason)
        vm.retryEpisodes()
    }

    @Test
    fun `the first season with episodes is selected before the specials`() {
        val withSpecials = seriesFixture(seasons = listOf(seasonFixture(0), seasonFixture(1), seasonFixture(2)))
        val vm = SeriesPageViewModel(FakeSeries(series = withSpecials), 1399)

        assertEquals(1, vm.ready().selectedSeason)
    }

    @Test
    fun `a show whose only season is the specials selects them`() {
        val vm = SeriesPageViewModel(FakeSeries(series = seriesFixture(seasons = listOf(seasonFixture(0)))), 1399)

        assertEquals(0, vm.ready().selectedSeason)
    }
}
