package com.cooper.binge.companion.seerr.ui.discover.title.season

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.discover.title.seasonFixture
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A tall window, so every season's header is on screen at once and the order of their positions can be read. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h3000dp")
class SeriesEpisodesScreenTest {
    @get:Rule
    val compose: ComposeContentTestRule = createSeerrComposeRule()

    private fun episodes(count: Int) = (1..count).map { SeasonEpisode(it, "Episode $it", "A synopsis of $it.", null, null, null) }

    private fun state(perSeason: Int) =
        SeriesEpisodesUiState.Ready(
            listOf(1, 2, 3).map { SeasonSection(seasonFixture(it), SeasonEpisodesState.Loaded(episodes(perSeason))) },
        )

    private fun show(
        state: SeriesEpisodesUiState,
        openAt: Int,
        near: MutableList<Int> = mutableListOf(),
    ) = compose.setContent {
        SeerrTheme {
            SeriesEpisodesScreen("A Show", state, openAt, { near += it }, {}, onBack = {}, onRetry = {})
        }
    }

    @Test
    fun `the list holds every season's header in order under the show's name`() {
        show(state(perSeason = 1), openAt = 1)

        compose.onNodeWithText("A Show").assertIsDisplayed()
        val tops =
            listOf(1, 2, 3).map {
                compose
                    .onNodeWithText("Season $it · 10 episodes")
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot()
                    .top
            }
        assertEquals("the headers run down the page in season order", tops.sorted(), tops)
        assertEquals(3, tops.toSet().size)
    }

    @Test
    fun `it opens at the season the show's page had chosen`() {
        show(state(perSeason = 12), openAt = 3)

        compose.onNodeWithText("Season 3 · 10 episodes").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTextCount("Season 1 · 10 episodes") == 0)
    }

    @Test
    fun `it opens at the chosen season when the seasons above it are not read yet`() {
        val seasons =
            SeriesEpisodesUiState.Ready(
                listOf(
                    SeasonSection(seasonFixture(1), SeasonEpisodesState.Loading),
                    SeasonSection(seasonFixture(2), SeasonEpisodesState.Loaded(episodes(60))),
                ),
            )
        show(seasons, openAt = 2)

        compose.onNodeWithText("Season 2 · 10 episodes").assertIsDisplayed()
        compose.onNodeWithText("Season 1 · 10 episodes").assertIsNotDisplayed()
    }

    @Test
    fun `an episode shows its synopsis`() {
        show(state(perSeason = 1), openAt = 1)

        assertEquals(3, compose.onAllNodesWithTextCount("A synopsis of 1."))
    }

    private fun ComposeContentTestRule.onAllNodesWithTextCount(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size
}
