package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.ui.settings.server.DiscoverSlider
import com.cooper.binge.companion.seerr.ui.settings.server.SliderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubRowsTest {
    private fun slider(
        type: SliderType,
        id: Int = type.code,
        enabled: Boolean = true,
        data: String? = null,
        title: String? = null,
    ) = DiscoverSlider(id = id, typeCode = type.code, title = title, builtIn = !type.custom, enabled = enabled, data = data)

    private fun List<DiscoverSlider>.movies() = rowsFor(HubTab.Movies)

    private fun List<DiscoverSlider>.series() = rowsFor(HubTab.Series)

    @Test
    fun `the built-in kinds split across the two hubs as the epic says`() {
        val all = SliderType.entries.filterNot { it.custom }.map { slider(it) }

        assertEquals(
            listOf("RecentlyAdded", "RecentRequests", "Watchlist", "Trending", "Popular", "Genres", "Upcoming", "Studios"),
            all.movies().map { it::class.simpleName },
        )
        assertEquals(
            listOf("RecentlyAdded", "RecentRequests", "Watchlist", "Trending", "Popular", "Genres", "Upcoming", "Networks"),
            all.series().map { it::class.simpleName },
        )
    }

    @Test
    fun `a mixed kind goes to both hubs narrowed to the one asking`() {
        val mixed =
            listOf(
                SliderType.Trending,
                SliderType.RecentlyAdded,
                SliderType.RecentRequests,
                SliderType.Watchlist,
            ).map { slider(it) }

        assertEquals(setOf(HubTab.Movies), mixed.movies().map { (it as HubRow.Mixed).tab }.toSet())
        assertEquals(setOf(HubTab.Series), mixed.series().map { (it as HubRow.Mixed).tab }.toSet())
        assertEquals(4, mixed.movies().size)
        assertEquals(4, mixed.series().size)
    }

    @Test
    fun `every custom kind reaches only its own hub with what it holds`() {
        val custom =
            listOf(
                slider(SliderType.MovieKeyword, id = 101, data = "9715,180547"),
                slider(SliderType.TvKeyword, id = 102, data = "4344"),
                slider(SliderType.MovieGenre, id = 103, data = "28"),
                slider(SliderType.TvGenre, id = 104, data = "18"),
                slider(SliderType.Studio, id = 105, data = "420"),
                slider(SliderType.Network, id = 106, data = "213"),
                slider(SliderType.Search, id = 107, data = "  star wars "),
                slider(SliderType.MovieStreamingServices, id = 108, data = "US,8|337"),
                slider(SliderType.TvStreamingServices, id = 109, data = "GB,9"),
            )

        assertEquals(
            listOf(
                HubRow.Keywords(101, null, HubTab.Movies, listOf(9715, 180547)),
                HubRow.Genre(103, null, HubTab.Movies, 28),
                HubRow.Studio(105, null, 420),
                HubRow.Search(107, null, HubTab.Movies, "star wars"),
                HubRow.Streaming(108, null, HubTab.Movies, "US", listOf(8, 337)),
            ),
            custom.movies(),
        )
        assertEquals(
            listOf(
                HubRow.Keywords(102, null, HubTab.Series, listOf(4344)),
                HubRow.Genre(104, null, HubTab.Series, 18),
                HubRow.Network(106, null, 213),
                HubRow.Search(107, null, HubTab.Series, "star wars"),
                HubRow.Streaming(109, null, HubTab.Series, "GB", listOf(9)),
            ),
            custom.series(),
        )
    }

    @Test
    fun `a custom slider keeps its own title`() {
        val row = listOf(slider(SliderType.MovieGenre, data = "28", title = "Action nights")).movies().single()

        assertEquals("Action nights", row.title)
    }

    @Test
    fun `a disabled slider is dropped and the server order is kept`() {
        val list =
            listOf(
                slider(SliderType.PopularMovies, id = 30),
                slider(SliderType.Trending, id = 10, enabled = false),
                slider(SliderType.UpcomingMovies, id = 20),
                slider(SliderType.MovieGenre, id = 5, data = "28"),
            )

        assertEquals(listOf(30, 20, 5), list.movies().map { it.sliderId })
    }

    @Test
    fun `a custom slider whose data does not parse is dropped from the hub`() {
        val broken =
            listOf(
                slider(SliderType.MovieKeyword, data = ""),
                slider(SliderType.MovieKeyword, data = "abc"),
                slider(SliderType.MovieGenre, data = "action"),
                slider(SliderType.Studio, data = null),
                slider(SliderType.Search, data = "   "),
                slider(SliderType.MovieStreamingServices, data = "US,"),
                slider(SliderType.MovieStreamingServices, data = ",8"),
                slider(SliderType.MovieStreamingServices, data = ""),
            )

        assertTrue(broken.movies().isEmpty())
    }

    @Test
    fun `a keyword list skips what is not an id and keeps the rest`() {
        val row = listOf(slider(SliderType.TvKeyword, data = "1, x ,3")).series().single() as HubRow.Keywords

        assertEquals(listOf(1, 3), row.keywordIds)
    }

    @Test
    fun `a slider of a kind this app does not know is dropped`() {
        val unknown = DiscoverSlider(id = 1, typeCode = 99, title = null, builtIn = false, enabled = true, data = "x")

        assertTrue(listOf(unknown).movies().isEmpty())
        assertTrue(listOf(unknown).series().isEmpty())
    }

    @Test
    fun `every kind appears on at least one hub`() {
        val data = mapOf(SliderType.MovieStreamingServices to "US,8", SliderType.TvStreamingServices to "US,8")
        SliderType.entries.forEach { type ->
            val one = slider(type, data = data[type] ?: "1")
            assertTrue(type.name, one.let { listOf(it) }.let { it.movies().isNotEmpty() || it.series().isNotEmpty() })
        }
    }

    @Test
    fun `the default set is the twelve built-ins in the server's order, all on and none mistaken for a server row`() {
        val defaults = defaultSliders()

        assertEquals((1..12).toList(), defaults.map { it.typeCode })
        assertTrue(defaults.all { it.enabled && it.builtIn && it.id < 0 })
        assertEquals(8, defaults.movies().size)
        assertEquals(8, defaults.series().size)
    }
}
