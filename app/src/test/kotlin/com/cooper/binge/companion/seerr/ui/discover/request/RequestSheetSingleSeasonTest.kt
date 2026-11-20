package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestAccess
import com.cooper.binge.companion.seerr.discover.request.SeasonChoice
import com.cooper.binge.companion.seerr.discover.request.SeasonChoices
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val EPISODES = 10

private fun season(
    number: Int,
    availability: TitleAvailability = TitleAvailability.None,
) = SeasonChoice(number, "Season $number", EPISODES, availability)

private fun sheet(
    seasons: List<SeasonChoice>,
    picked: Set<Int> = emptySet(),
) = RequestSheetUiState.Ready(
    target =
        RequestSheetTarget(
            kind = TitleKind.Series,
            tmdbId = 1399,
            title = "The Long Winter",
            seasonChoices = SeasonChoices(seasons, seasons),
            partialRequests = true,
            meta = "2011",
        ),
    access = RequestAccess(may4k = false, mayChooseDestination = false),
    quota = QuotaState.Known(null),
    pickedSeasons = picked,
)

/** A show with one season left to request has it as the Seasons row itself, toggled in place; several keep the row that opens the list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class RequestSheetSingleSeasonTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private val toggled = mutableListOf<Int>()

    private fun show(state: RequestSheetUiState.Ready) {
        rule.setContent {
            SeerrTheme {
                RequestSheetContent(state, RequestSheetActions({}, { toggled += it }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}))
            }
        }
        rule.waitForIdle()
    }

    private val oneOpen = listOf(season(1, TitleAvailability.Available), season(2, TitleAvailability.Pending), season(3))

    @Test
    fun `one open season is the row, named, and a tap on the row toggles it in place`() {
        show(sheet(oneOpen))

        rule.onNodeWithText("Season 3").performClick()

        assertEquals(listOf(3), toggled)
        rule.onNodeWithText("Seasons").assertDoesNotExist()
    }

    @Test
    fun `its box toggles the same season`() {
        show(sheet(oneOpen))

        rule.onNode(isToggleable(), useUnmergedTree = true).performClick()

        assertEquals(listOf(3), toggled)
    }

    @Test
    fun `a picked season shows its box ticked`() {
        show(sheet(oneOpen, picked = setOf(3)))

        rule.onNode(isToggleable(), useUnmergedTree = true).assertIsOn()
    }

    @Test
    fun `several open seasons keep the Seasons row and its count`() {
        show(sheet(listOf(season(1), season(2), season(3, TitleAvailability.Available))))

        rule.onNodeWithText("Seasons").assertExists()
        rule.onNodeWithText("0 of 2 selected").assertExists()
        rule.onNodeWithText("Season 1").assertDoesNotExist()
    }

    @Test
    fun `no open season keeps today's row`() {
        show(sheet(listOf(season(1, TitleAvailability.Available), season(2, TitleAvailability.Pending))))

        rule.onNodeWithText("Seasons").assertExists()
    }
}
