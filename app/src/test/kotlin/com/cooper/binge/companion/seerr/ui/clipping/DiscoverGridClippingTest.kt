package com.cooper.binge.companion.seerr.ui.clipping

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.grid.ActiveFilterChip
import com.cooper.binge.companion.seerr.ui.grid.ChipKind
import com.cooper.binge.companion.seerr.ui.grid.DiscoverGridActions
import com.cooper.binge.companion.seerr.ui.grid.DiscoverGridScreen
import com.cooper.binge.companion.seerr.ui.grid.DiscoverGridUiState
import com.cooper.binge.companion.seerr.ui.grid.GridControls
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private fun card(
    id: Int,
    name: String,
    availability: TitleAvailability,
) = HubCard(TitleKind.Movie, id, name, "2021", null, availability, "https://server.example/movie/$id")

private val CONTROLS =
    GridControls(
        DiscoverSort.RevenueDesc,
        ActiveFilterChip(ChipKind.Genre, "A genre with a name long enough to need the whole row"),
    )

private val STATES: List<DiscoverGridUiState> =
    listOf(
        DiscoverGridUiState.Ready(
            CONTROLS,
            listOf(
                card(1, "A Title That Is Long Enough To Wrap Onto Several Lines On A Phone", TitleAvailability.PartiallyAvailable),
                card(2, "Sintel", TitleAvailability.Pending),
                card(3, "Tears of Steel", TitleAvailability.Processing),
                card(4, "Metropolis", TitleAvailability.Blocklisted),
            ),
            hasMore = true,
            appending = false,
            appendFailed = true,
        ),
        DiscoverGridUiState.Empty(CONTROLS),
        DiscoverGridUiState.Error(CONTROLS, SeerrError.Unreachable),
    )

private val NO_ACTIONS = DiscoverGridActions({}, {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> })

/** The grid at its worst, at font scales 1.3 and 2.0, through the design system's `assertNoTextClipped`. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h4000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoverGridClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(
        state: DiscoverGridUiState,
        fontScale: Float,
    ) = rule.assertNoClippingAt(fontScale) { DiscoverGridScreen(TitleKind.Movie, null, state, NO_ACTIONS) }

    @Test fun `posters at 1_3`() = check(STATES[0], FONT_SCALE_13)

    @Test fun `posters at 2_0`() = check(STATES[0], FONT_SCALE_20)

    @Test fun `empty at 2_0`() = check(STATES[1], FONT_SCALE_20)

    @Test fun `a failure at 2_0`() = check(STATES[2], FONT_SCALE_20)
}

/** The same states in Spanish, whose strings are the longer. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-w412dp-h4000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoverGridSpanishClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(
        state: DiscoverGridUiState,
        fontScale: Float,
    ) = rule.assertNoClippingAt(fontScale) { DiscoverGridScreen(TitleKind.Movie, null, state, NO_ACTIONS) }

    @Test fun `posters at 1_3`() = check(STATES[0], FONT_SCALE_13)

    @Test fun `posters at 2_0`() = check(STATES[0], FONT_SCALE_20)

    @Test fun `empty at 2_0`() = check(STATES[1], FONT_SCALE_20)

    @Test fun `a failure at 2_0`() = check(STATES[2], FONT_SCALE_20)
}
