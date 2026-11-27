package com.cooper.binge.companion.seerr.ui.discover.title.season

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.discover.title.SeriesDetailsRepository
import com.cooper.binge.companion.seerr.discover.title.TitleExtras
import com.cooper.binge.companion.seerr.ui.discover.title.seasonFixture
import com.cooper.binge.companion.seerr.ui.discover.title.seriesFixture
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeEpisode(
    var series: SeriesDetails = seriesFixture(listOf(seasonFixture(1), seasonFixture(2), seasonFixture(3))),
) : SeriesDetailsRepository {
    val seriesReads = mutableListOf<Int>()

    override suspend fun series(tmdbId: Int): SeriesDetails {
        seriesReads += tmdbId
        return series
    }

    override suspend fun season(
        tmdbId: Int,
        seasonNumber: Int,
    ): List<SeasonEpisode> =
        listOf(
            SeasonEpisode(1, "Pilot $seasonNumber", "A synopsis.", null, null, null),
            SeasonEpisode(2, "Second $seasonNumber", null, null, null, null),
        )

    override suspend fun extras(tmdbId: Int): TitleExtras = error("not read here")

    override suspend fun serverProfile() = null

    override suspend fun unblock(tmdbId: Int) = error("not used here")

    override suspend fun block(
        tmdbId: Int,
        title: String,
    ) = error("not used here")

    override suspend fun setWatchlist(
        tmdbId: Int,
        title: String,
        onList: Boolean,
    ) = error("not used here")
}

class SeriesEpisodeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun ready(vm: SeriesEpisodeViewModel) = vm.uiState.value as SeriesEpisodeUiState.Ready

    @Test
    fun `the episode is read from its season by number`() {
        val vm = SeriesEpisodeViewModel(FakeEpisode(), tmdbId = 1399, seasonNumber = 2, episodeNumber = 2)

        assertEquals("Second 2", ready(vm).episode.name)
        assertEquals(2, ready(vm).seasonNumber)
    }

    @Test
    fun `the request is limited to the episode's season in each version`() {
        val series =
            seriesFixture(
                listOf(seasonFixture(1), seasonFixture(2), seasonFixture(3)),
                fourK = null,
            )
        val vm = SeriesEpisodeViewModel(FakeEpisode(series), 1399, seasonNumber = 2, episodeNumber = 1)

        val state = ready(vm)
        assertEquals(2, state.standard?.number)
        assertTrue(state.standardOpen)
        assertFalse("the show has no 4K version, so 4K is not offered", state.fourKOpen)
    }

    @Test
    fun `a season that is already requested or available is not offered again`() {
        val series = seriesFixture(listOf(seasonFixture(1), seasonFixture(2, standard = TitleAvailability.Available)))
        val vm = SeriesEpisodeViewModel(FakeEpisode(series), 1399, seasonNumber = 2, episodeNumber = 1)

        assertFalse(ready(vm).standardOpen)
        assertFalse(ready(vm).canRequest)
    }

    @Test
    fun `a server that takes a whole show offers no season request`() {
        val series = seriesFixture(listOf(seasonFixture(1), seasonFixture(2))).copy(partialRequests = false)
        val vm = SeriesEpisodeViewModel(FakeEpisode(series), 1399, seasonNumber = 2, episodeNumber = 1)

        assertFalse(ready(vm).canRequest)
    }

    @Test
    fun `an episode its season does not list is an error, not an empty page`() {
        val vm = SeriesEpisodeViewModel(FakeEpisode(), 1399, seasonNumber = 2, episodeNumber = 9)

        assertTrue(vm.uiState.value is SeriesEpisodeUiState.Error)
    }

    @Test
    fun `closing the request sheet reads the episode again`() {
        val repository = FakeEpisode()
        val vm = SeriesEpisodeViewModel(repository, 1399, seasonNumber = 2, episodeNumber = 1)
        assertEquals(1, repository.seriesReads.size)

        vm.openRequestSheet(fourK = false)
        assertTrue(ready(vm).requestSheetOpen)

        vm.closeRequestSheet()

        assertFalse(ready(vm).requestSheetOpen)
        assertEquals("the page reads the show again once the sheet closes", 2, repository.seriesReads.size)
    }
}
