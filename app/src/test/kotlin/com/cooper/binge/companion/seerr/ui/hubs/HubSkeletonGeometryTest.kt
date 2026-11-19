package com.cooper.binge.companion.seerr.ui.hubs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubHero
import com.cooper.binge.companion.seerr.discover.sliders.HubRow
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.discover.title.TitleRatings
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hub's skeleton reserves what the loaded hub fills, in position and in size (#2235): the hero, the Browse all row and the
 * first rail, which is the shape both hubs normally have.
 *
 * Size is the point: a plate that starts at the same corner but is shorter leaves every row after the first landing
 * somewhere else. The rail with "See all" is taller than the one without, because its header carries a button floored at the
 * touch target, so both are checked.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubSkeletonGeometryTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the skeleton holds a rail with See all where it lands`() = check(HubRow.Trending(1, null, HubTab.Movies))

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the skeleton holds a rail without See all where it lands`() = check(HubRow.RecentlyAdded(1, null, HubTab.Movies))

    @Test
    @Config(qualifiers = TABLET)
    fun `on a tablet the skeleton holds a rail with See all where it lands`() = check(HubRow.Trending(1, null, HubTab.Movies))

    @Test
    @Config(qualifiers = TABLET)
    fun `on a tablet the skeleton holds a rail without See all where it lands`() = check(HubRow.RecentlyAdded(1, null, HubTab.Movies))

    @Test
    @Config(qualifiers = PHONE)
    fun `at a larger font the skeleton still holds the first rail where it lands`() =
        check(HubRow.Trending(1, null, HubTab.Movies), LARGE_FONT)

    @Test
    @Config(qualifiers = PHONE)
    fun `at the largest font the skeleton still holds the first rail where it lands`() =
        check(HubRow.Trending(1, null, HubTab.Movies), LARGEST_FONT)

    @Test
    @Config(qualifiers = PHONE)
    fun `a hero that answers after the rows does not move them`() = check(TRENDING, loading = ready(TRENDING, hero = emptyList()))

    @Test
    @Config(qualifiers = TABLET)
    fun `on a tablet a hero that answers after the rows does not move them`() =
        check(TRENDING, loading = ready(TRENDING, hero = emptyList()))

    @Test
    @Config(qualifiers = PHONE)
    fun `a hero that fails or comes back empty does not move the rows`() = check(TRENDING, loaded = ready(TRENDING, hero = emptyList()))

    @Test
    @Config(qualifiers = TABLET)
    fun `on a tablet a hero that fails or comes back empty does not move the rows`() =
        check(TRENDING, loaded = ready(TRENDING, hero = emptyList()))

    @Test
    @Config(qualifiers = PHONE)
    fun `review scores and a status on the hero do not change its height or move the rows`() =
        check(TRENDING, loading = ready(TRENDING), loaded = ready(TRENDING, hero = listOf(scoredHero())))

    @Test
    @Config(qualifiers = TABLET)
    fun `on a tablet review scores and a status on the hero do not change its height`() =
        check(TRENDING, loading = ready(TRENDING), loaded = ready(TRENDING, hero = listOf(scoredHero())))

    @Test
    @Config(qualifiers = PHONE)
    fun `at a larger font review scores and a status on the hero still leave its height alone`() =
        check(TRENDING, LARGE_FONT, loading = ready(TRENDING), loaded = ready(TRENDING, hero = listOf(scoredHero())))

    @Test
    @Config(qualifiers = PHONE)
    fun `at the largest font review scores and a status on the hero still leave its height alone`() =
        check(TRENDING, LARGEST_FONT, loading = ready(TRENDING), loaded = ready(TRENDING, hero = listOf(scoredHero())))

    @Test
    @Config(qualifiers = PHONE)
    fun `where the cached profile says the server cannot filter, the skeleton has no Browse all place and the rows do not move`() =
        check(
            TRENDING,
            loading = DiscoverHubUiState.Loading(browseAll = false),
            loaded = ready(TRENDING).copy(browseAll = false),
            browseAllPlace = false,
        )

    @Test
    @Config(qualifiers = PHONE)
    fun `with no cached profile the skeleton keeps the Browse all place for a server that can filter`() = check(TRENDING)

    private fun check(
        row: HubRow,
        fontScale: Float = 1f,
        loading: DiscoverHubUiState = DiscoverHubUiState.Loading(),
        loaded: DiscoverHubUiState = ready(row),
        browseAllPlace: Boolean = true,
    ) {
        // Where Discover is a tab of its own the Browse all row is not drawn, so there is nothing for the skeleton to reserve.
        val discoverTab = RuntimeEnvironment.getQualifiers().contains("w840dp")
        val anchors =
            listOfNotNull(
                LayoutAnchors.Hub.HERO,
                LayoutAnchors.Hub.DISCOVER_ENTRY.takeUnless { discoverTab || !browseAllPlace },
                LayoutAnchors.Hub.FIRST_CAROUSEL,
            ).map(LayoutAnchors::section)
        rule.assertSkeletonReservesGeometry(anchors = anchors, checkSize = true) { resolved ->
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SeerrTheme {
                    DiscoverHubScreen(
                        tab = HubTab.Movies,
                        state = if (resolved) loaded else loading,
                        onRefresh = {},
                        onOpenTitle = {},
                        onOpenGrid = { _, _ -> },
                        onOpenRequests = {},
                    )
                }
            }
        }
    }

    private fun ready(
        row: HubRow,
        hero: List<HubHero> = listOf(hero()),
    ) = DiscoverHubUiState.Ready(
        listOf(HubRowUi.Cards(row, List(CARDS) { card(it) })),
        pending = 0,
        refreshing = false,
        hero = hero,
    )

    private fun hero() =
        HubHero(
            TitleKind.Movie,
            1,
            "Paper Moons",
            "https://server.example/backdrop.jpg",
            "2020",
            TitleAvailability.None,
            "https://server.example/movie/1",
        )

    private fun scoredHero() = hero().copy(availability = TitleAvailability.Available, ratings = TitleRatings(97, 91, 7.9))

    private fun card(id: Int) =
        HubCard(TitleKind.Movie, id, "Movie $id", "2020", null, TitleAvailability.None, "https://server.example/movie/$id")

    private companion object {
        const val PHONE = "w411dp-h891dp-xhdpi"
        const val TABLET = "w840dp-h1280dp-xhdpi"
        const val CARDS = 6
        val TRENDING = HubRow.Trending(1, null, HubTab.Movies)
        const val LARGE_FONT = 1.3f
        const val LARGEST_FONT = 2f
    }
}
