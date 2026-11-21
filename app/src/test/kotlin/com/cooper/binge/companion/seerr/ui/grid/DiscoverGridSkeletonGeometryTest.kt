package com.cooper.binge.companion.seerr.ui.grid

import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The grid's skeleton reserves what its first loaded cell fills, in position and in size (#2235).
 *
 * The anchor is the **first cell**: the grid fills the screen, so its own top never moves, and everything above the first cell
 * (the bar, the chips and the padding) is what the skeleton has to reproduce. Size is on because a poster plate that starts at
 * the same corner but is shorter than the card, or a column count that differs, agrees on every edge a position check
 * measures and puts every later cell somewhere else.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoverGridSkeletonGeometryTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the skeleton holds the first cell where the grid puts it`() = check()

    @Test
    @Config(qualifiers = PHONE, fontScale = 1.3f)
    fun `on a phone at a font scale of 1 point 3 the skeleton holds the first cell where the grid puts it`() = check()

    @Test
    @Config(qualifiers = PHONE, fontScale = 2f)
    fun `on a phone at a font scale of 2 the skeleton holds the first cell where the grid puts it`() = check()

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `on a phone turned sideways the skeleton holds the first cell where the grid puts it`() = check()

    @Test
    @Config(qualifiers = TABLET)
    fun `on a tablet the skeleton holds the first cell where the grid puts it`() = check()

    private fun check() {
        val anchor = LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)
        rule.assertSkeletonReservesGeometry(anchors = listOf(anchor), checkSize = true) { resolved ->
            SeerrTheme {
                DiscoverGridScreen(
                    kind = TitleKind.Movie,
                    title = "Drama",
                    state = if (resolved) ready() else DiscoverGridUiState.Loading(CONTROLS),
                    actions = DiscoverGridActions({}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, FilterLoads({}, {}, {})),
                )
            }
        }
    }

    private fun ready() =
        DiscoverGridUiState.Ready(
            CONTROLS,
            List(CARDS) {
                card(it)
            },
            hasMore = false,
            appending = false,
            appendFailed = false,
        )

    private fun card(id: Int) =
        HubCard(TitleKind.Movie, id, "Movie $id", "2021", null, TitleAvailability.None, "https://server.example/movie/$id", 7.5f)

    private companion object {
        const val PHONE = "w411dp-h891dp-xhdpi"
        const val PHONE_LANDSCAPE = "w891dp-h411dp-xhdpi"
        const val TABLET = "w840dp-h1280dp-xhdpi"
        const val CARDS = 12
        val CONTROLS = GridControls(DiscoverSort.PopularityDesc, ActiveFilterChip(ChipKind.Genre, "Drama"))
    }
}
