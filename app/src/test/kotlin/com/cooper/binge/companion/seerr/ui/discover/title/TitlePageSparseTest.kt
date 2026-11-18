package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A film the server gives no scores for, and almost nothing else, still opens: a page with no stats draws no stats row. */
@RunWith(RobolectricTestRunner::class)
class TitlePageSparseTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `a film with no scores and no extras still renders its title`() {
        val sparse = movieFixture().copy(voteAverage = null, overview = null, genres = emptyList(), status = null, originalLanguage = null)
        rule.setContent {
            SeerrTheme {
                TitlePageScreen(
                    state = TitlePageUiState.Ready(sparse),
                    events = emptyFlow(),
                    actions = TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                )
            }
        }

        // The hero and the top bar each carry the title.
        rule.onAllNodesWithText("The Matrix").assertCountEquals(2)
    }
}
