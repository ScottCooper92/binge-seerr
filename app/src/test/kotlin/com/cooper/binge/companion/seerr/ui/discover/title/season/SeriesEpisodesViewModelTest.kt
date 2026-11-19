package com.cooper.binge.companion.seerr.ui.discover.title.season

import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.discover.title.SeriesDetailsRepository
import com.cooper.binge.companion.seerr.discover.title.TitleExtras
import com.cooper.binge.companion.seerr.ui.discover.title.seasonFixture
import com.cooper.binge.companion.seerr.ui.discover.title.seriesFixture
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

private class FakeEpisodes(
    var series: SeriesDetails = seriesFixture(listOf(seasonFixture(0), seasonFixture(2), seasonFixture(1), seasonFixture(3))),
    var failSeries: Throwable? = null,
    val failing: MutableSet<Int> = mutableSetOf(),
) : SeriesDetailsRepository {
    val seasonReads = mutableListOf<Int>()

    override suspend fun season(
        tmdbId: Int,
        seasonNumber: Int,
    ): List<SeasonEpisode> {
        seasonReads += seasonNumber
        if (seasonNumber in failing) throw IOException("down")
        return listOf(SeasonEpisode(1, "Pilot $seasonNumber", "A synopsis.", null, null, null))
    }

    override suspend fun series(tmdbId: Int): SeriesDetails = failSeries?.let { throw it } ?: series

    override suspend fun extras(tmdbId: Int): TitleExtras = error("not read here")

    override suspend fun unblock(tmdbId: Int) = error("not used here")

    override suspend fun setWatchlist(
        tmdbId: Int,
        title: String,
        onList: Boolean,
    ) = error("not used here")
}

class SeriesEpisodesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun ready(vm: SeriesEpisodesViewModel) = vm.uiState.value as SeriesEpisodesUiState.Ready

    @Test
    fun `every season is on the page in order with specials last`() {
        val vm = SeriesEpisodesViewModel(FakeEpisodes(), tmdbId = 1399, seasonNumber = 2)

        assertEquals(listOf(1, 2, 3, 0), ready(vm).seasons.map { it.season.number })
    }

    @Test
    fun `only the chosen season is read until the page scrolls near another`() {
        val repository = FakeEpisodes()
        val vm = SeriesEpisodesViewModel(repository, 1399, seasonNumber = 2)

        assertEquals(listOf(2), repository.seasonReads)
        assertTrue(ready(vm).seasons.first { it.season.number == 1 }.episodes is SeasonEpisodesState.Loading)

        vm.onSeasonNear(3)
        vm.onSeasonNear(3)

        assertEquals("a season is read once", listOf(2, 3), repository.seasonReads)
        val loaded = ready(vm).seasons.first { it.season.number == 3 }.episodes as SeasonEpisodesState.Loaded
        assertEquals(listOf("Pilot 3"), loaded.episodes.map { it.name })
        assertEquals("A synopsis.", loaded.episodes.single().overview)
    }

    @Test
    fun `a season that fails keeps the page and retries on its own`() {
        val repository = FakeEpisodes(failing = mutableSetOf(1))
        val vm = SeriesEpisodesViewModel(repository, 1399, seasonNumber = 2)

        vm.onSeasonNear(1)
        assertTrue(ready(vm).seasons.first { it.season.number == 1 }.episodes is SeasonEpisodesState.Failed)
        assertTrue(ready(vm).seasons.first { it.season.number == 2 }.episodes is SeasonEpisodesState.Loaded)

        repository.failing.clear()
        vm.retrySeason(1)

        assertTrue(ready(vm).seasons.first { it.season.number == 1 }.episodes is SeasonEpisodesState.Loaded)
    }

    @Test
    fun `a failed show is an error that retries`() {
        val repository = FakeEpisodes(failSeries = IOException("down"))
        val vm = SeriesEpisodesViewModel(repository, 1399, 1)
        assertTrue(vm.uiState.value is SeriesEpisodesUiState.Error)

        repository.failSeries = null
        vm.load()

        assertTrue(vm.uiState.value is SeriesEpisodesUiState.Ready)
        assertEquals(listOf(1), repository.seasonReads)
    }
}
