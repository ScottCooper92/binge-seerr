package com.cooper.binge.companion.seerr.ui.discover.title.season

import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.discover.title.SeriesDetailsRepository
import com.cooper.binge.companion.seerr.discover.title.TitleExtras
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

private class FakeSeason(
    var fail: Throwable? = null,
    val episodes: List<SeasonEpisode> = listOf(SeasonEpisode(1, "Pilot", null, null, null), SeasonEpisode(2, "Second", null, null, null)),
) : SeriesDetailsRepository {
    val reads = mutableListOf<Pair<Int, Int>>()

    override suspend fun season(
        tmdbId: Int,
        seasonNumber: Int,
    ): List<SeasonEpisode> {
        reads += tmdbId to seasonNumber
        fail?.let { throw it }
        return episodes
    }

    override suspend fun series(tmdbId: Int): SeriesDetails = error("not read here")

    override suspend fun extras(tmdbId: Int): TitleExtras = error("not read here")
}

class SeasonEpisodesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `the season loads its episodes in order`() {
        val repository = FakeSeason()
        val vm = SeasonEpisodesViewModel(repository, tmdbId = 1399, seasonNumber = 3)

        assertEquals(listOf(1399 to 3), repository.reads)
        assertEquals(listOf("Pilot", "Second"), (vm.uiState.value as SeasonEpisodesUiState.Ready).episodes.map { it.name })
    }

    @Test
    fun `a failed load is an error that retries`() {
        val repository = FakeSeason(fail = IOException("down"))
        val vm = SeasonEpisodesViewModel(repository, 1399, 1)
        assertTrue(vm.uiState.value is SeasonEpisodesUiState.Error)

        repository.fail = null
        vm.load()

        assertTrue(vm.uiState.value is SeasonEpisodesUiState.Ready)
    }

    @Test
    fun `a season the server lists no episodes for is ready and empty`() {
        val vm = SeasonEpisodesViewModel(FakeSeason(episodes = emptyList()), 1399, 1)

        assertTrue((vm.uiState.value as SeasonEpisodesUiState.Ready).episodes.isEmpty())
    }
}
