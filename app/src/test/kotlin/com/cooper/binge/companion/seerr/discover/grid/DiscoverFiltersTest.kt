package com.cooper.binge.companion.seerr.discover.grid

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.SeerrVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverFiltersTest {
    private val seerr3 = SeerrServerProfile(variant = SeerrVariant.Seerr, version = SeerrVersion(3, 2, 0))
    private val jellyseerr2 = SeerrServerProfile(variant = SeerrVariant.Jellyseerr, version = SeerrVersion(2, 0, 0))
    private val jellyseerr26 = SeerrServerProfile(variant = SeerrVariant.Jellyseerr, version = SeerrVersion(2, 6, 0))
    private val overseerr = SeerrServerProfile(variant = SeerrVariant.Overseerr, version = SeerrVersion(1, 33, 0))

    private fun query(
        kind: TitleKind = TitleKind.Movie,
        filters: DiscoverFilters = DiscoverFilters(),
        sort: DiscoverSort = DiscoverSort.PopularityDesc,
        page: Int = 1,
        profile: SeerrServerProfile = seerr3,
    ) = toDiscoverQuery(kind, filters, sort, page, profile)

    @Test
    fun `genres are joined with a comma for all of them and a pipe for any of them`() {
        val all = DiscoverFilters(genres = setOf(18, 35))
        assertEquals("18,35", query(filters = all)["genre"])
        assertEquals("18|35", query(filters = all.copy(genresAny = true))["genre"])
    }

    @Test
    fun `no filters sends the page and the order only`() {
        assertEquals(mapOf("page" to "1", "sortBy" to "popularity.desc"), query())
        assertEquals(mapOf("page" to "3", "sortBy" to "vote_average.asc"), query(sort = DiscoverSort.RatingAsc, page = 3))
    }

    @Test
    fun `a movie names its studio and its release date and a show its network and first air date`() {
        val filters = DiscoverFilters(company = 420, releaseFrom = "2024-01-01", releaseTo = "2024-12-31")
        val movie = query(TitleKind.Movie, filters)
        assertEquals("420", movie["studio"])
        assertEquals("2024-01-01", movie["primaryReleaseDateGte"])
        assertEquals("2024-12-31", movie["primaryReleaseDateLte"])
        assertNull(movie["network"])
        val show = query(TitleKind.Series, filters, DiscoverSort.FirstAirOldest)
        assertEquals("420", show["network"])
        assertEquals("2024-01-01", show["firstAirDateGte"])
        assertEquals("2024-12-31", show["firstAirDateLte"])
        assertNull(show["studio"])
        assertNull(show["primaryReleaseDateGte"])
    }

    @Test
    fun `lists are joined and sorted so the same filters make the same query`() {
        val q = query(filters = DiscoverFilters(genres = setOf(35, 28), keywords = setOf(9, 3), providers = setOf(8, 337), region = "GB"))
        assertEquals("28,35", q["genre"])
        assertEquals("3,9", q["keywords"])
        assertEquals("8|337", q["watchProviders"])
        assertEquals("GB", q["watchRegion"])
    }

    @Test
    fun `a region with no streaming service is not sent`() {
        assertNull(query(filters = DiscoverFilters(region = "GB"))["watchRegion"])
    }

    @Test
    fun `runtime rating votes and language each name their own parameter`() {
        val q =
            query(
                filters =
                    DiscoverFilters(
                        language = "ja",
                        runtimeMin = 60,
                        runtimeMax = 180,
                        ratingMin = 6.5,
                        ratingMax = 9.0,
                        voteCountMin = 100,
                        voteCountMax = 5000,
                    ),
            )
        assertEquals("ja", q["language"])
        assertEquals("60", q["withRuntimeGte"])
        assertEquals("180", q["withRuntimeLte"])
        assertEquals("6.5", q["voteAverageGte"])
        assertEquals("9.0", q["voteAverageLte"])
        assertEquals("100", q["voteCountGte"])
        assertEquals("5000", q["voteCountLte"])
    }

    @Test
    fun `an order that makes no sense for the kind is not sent`() {
        assertNull(query(TitleKind.Series, sort = DiscoverSort.RevenueDesc)["sortBy"])
        assertEquals("first_air_date.desc", query(TitleKind.Series, sort = DiscoverSort.FirstAirNewest)["sortBy"])
        assertEquals("release_date.desc", query(TitleKind.Movie, sort = DiscoverSort.ReleaseNewest)["sortBy"])
    }

    @Test
    fun `the parameters a server lacks are left out`() {
        val filters =
            DiscoverFilters(
                excludeKeywords = setOf(1),
                status = setOf(0, 2),
                certificationCountry = "US",
                certification = "PG-13",
            )
        val onSeerr = query(TitleKind.Series, filters, profile = seerr3)
        assertEquals("1", onSeerr["excludeKeywords"])
        assertEquals("0|2", onSeerr["status"])
        assertEquals("PG-13", onSeerr["certification"])
        assertEquals("US", onSeerr["certificationCountry"])

        val onJellyseerr2 = query(TitleKind.Series, filters, profile = jellyseerr2)
        assertNull("excluded keywords arrived with 3.0", onJellyseerr2["excludeKeywords"])
        assertEquals("a show's status is there from Jellyseerr 2.0", "0|2", onJellyseerr2["status"])
        assertNull("certification arrived with Jellyseerr 2.6", onJellyseerr2["certification"])
        assertEquals("PG-13", query(TitleKind.Series, filters, profile = jellyseerr26)["certification"])

        val onOverseerr = query(TitleKind.Series, filters, profile = overseerr)
        assertNull(onOverseerr["excludeKeywords"])
        assertNull(onOverseerr["status"])
        assertNull(onOverseerr["certification"])
    }

    @Test
    fun `a status filter is for a show and a certification needs its country`() {
        val filters = DiscoverFilters(status = setOf(3), certification = "R")
        val movie = query(TitleKind.Movie, filters)
        assertNull(movie["status"])
        assertNull("a certification with no country is not sent", movie["certification"])
    }

    @Test
    fun `every preset starts on the filters of the row it came from`() {
        val today = "2026-11-18"
        assertEquals(DiscoverFilters(), GridPreset.Popular.startingFilters(today))
        assertEquals(DiscoverFilters(releaseFrom = today), GridPreset.Upcoming.startingFilters(today))
        assertEquals(DiscoverFilters(genres = setOf(18)), GridPreset.Genre(18).startingFilters(today))
        assertEquals(DiscoverFilters(company = 213), GridPreset.Company(213).startingFilters(today))
        assertEquals(DiscoverFilters(language = "ko"), GridPreset.Language("ko").startingFilters(today))
        assertEquals(DiscoverFilters(keywords = setOf(9715)), GridPreset.Keyword(9715).startingFilters(today))
        assertEquals(DiscoverFilters(providers = setOf(8), region = "GB"), GridPreset.Provider(8, "GB").startingFilters(today))
        assertTrue(GridPreset.Trending.startingFilters(today).isEmpty)
        assertTrue(GridPreset.Watchlist.startingFilters(today).isEmpty)
    }

    @Test
    fun `upcoming starts soonest first and the others most popular first`() {
        assertEquals(DiscoverSort.ReleaseOldest, GridPreset.Upcoming.startingSort(TitleKind.Movie))
        assertEquals(DiscoverSort.FirstAirOldest, GridPreset.Upcoming.startingSort(TitleKind.Series))
        assertEquals(DiscoverSort.PopularityDesc, GridPreset.Genre(18).startingSort(TitleKind.Movie))
    }

    @Test
    fun `trending and the watchlist are not filterable`() {
        assertFalse(GridPreset.Trending.isFilterable)
        assertFalse(GridPreset.Watchlist.isFilterable)
        assertTrue(GridPreset.Popular.isFilterable)
        assertTrue(GridPreset.Provider(8, "GB").isFilterable)
    }

    @Test
    fun `each kind is offered only the orders that fit it`() {
        assertTrue(DiscoverSort.RevenueDesc in DiscoverSort.forKind(TitleKind.Movie))
        assertFalse(DiscoverSort.RevenueDesc in DiscoverSort.forKind(TitleKind.Series))
        assertTrue(DiscoverSort.FirstAirNewest in DiscoverSort.forKind(TitleKind.Series))
        assertFalse(DiscoverSort.FirstAirNewest in DiscoverSort.forKind(TitleKind.Movie))
        assertEquals(12, DiscoverSort.forKind(TitleKind.Movie).size)
        assertEquals(10, DiscoverSort.forKind(TitleKind.Series).size)
    }

    @Test
    fun `a preset's dedicated source names its kind`() {
        assertEquals("ByGenre", GridPreset.Genre(28).toSource(TitleKind.Movie)::class.simpleName)
        assertEquals("Trending", GridPreset.Trending.toSource(TitleKind.Series)::class.simpleName)
    }

    @Test
    fun `the applied count is one for each row that is set`() {
        assertEquals(0, DiscoverFilters().appliedCount())
        assertEquals(3, DiscoverFilters(genres = setOf(18, 35), runtimeMin = 90, language = "ja").appliedCount())
        assertEquals(1, DiscoverFilters(releaseFrom = "2020-01-01", releaseTo = "2021-01-01").appliedCount())
    }

    @Test
    fun `matching any genre means nothing with no genre chosen`() {
        assertTrue(DiscoverFilters(genresAny = true).isEmpty)
        assertFalse(DiscoverFilters(genres = setOf(18), genresAny = true).isEmpty)
    }
}
