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
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.awaitEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
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

        vm.closeStatusSheet()
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

        vm.closeManageSheet()
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

        vm.requestFourKFromManage()

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

        vm.requestFourKFromManage()

        assertTrue(vm.ready().requestSheetFourK)
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
