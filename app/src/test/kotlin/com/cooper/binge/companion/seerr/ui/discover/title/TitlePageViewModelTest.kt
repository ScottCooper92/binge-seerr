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
import com.cooper.binge.companion.seerr.discover.title.VersionState
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
    val collectionReads = mutableListOf<Int>()

    override suspend fun collectionParts(collectionId: Int): List<TitleResult> {
        collectionReads += collectionId
        return failCollection?.let { throw it } ?: collection
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
    fun `a 4K version alone is enough to offer the request`() {
        val movie = movieFixture(VersionState(TitleAvailability.Available, false), VersionState(TitleAvailability.None, true))

        assertTrue(TitlePageViewModel(FakeTitles(movie = movie), 603).ready().mayRequest)
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
