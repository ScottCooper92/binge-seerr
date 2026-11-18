package com.cooper.binge.companion.seerr.ui.hubs

import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubRow
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hub's skeleton reserves what its first loaded row fills, in position and in size (#2235).
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

    private fun check(row: HubRow) {
        val anchors = listOf(LayoutAnchors.Hub.DISCOVER_ENTRY, LayoutAnchors.Hub.FIRST_CAROUSEL).map(LayoutAnchors::section)
        rule.assertSkeletonReservesGeometry(anchors = anchors, checkSize = true) { resolved ->
            SeerrTheme {
                DiscoverHubScreen(
                    tab = HubTab.Movies,
                    state = if (resolved) ready(row) else DiscoverHubUiState.Loading,
                    onRefresh = {},
                    onOpenTitle = {},
                    onOpenGrid = { _, _ -> },
                    onOpenRequests = {},
                )
            }
        }
    }

    private fun ready(row: HubRow) =
        DiscoverHubUiState.Ready(
            listOf(HubRowUi.Cards(row, List(CARDS) { card(it) })),
            pending = 0,
            refreshing = false,
        )

    private fun card(id: Int) =
        HubCard(TitleKind.Movie, id, "Movie $id", "2020", null, TitleAvailability.None, "https://server.example/movie/$id")

    private companion object {
        const val PHONE = "w411dp-h891dp-xhdpi"
        const val TABLET = "w840dp-h1280dp-xhdpi"
        const val CARDS = 6
    }
}
