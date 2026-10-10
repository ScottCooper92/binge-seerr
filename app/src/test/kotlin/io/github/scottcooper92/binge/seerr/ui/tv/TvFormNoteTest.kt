package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A note is announced when it appears: a failure interrupts, anything else waits its turn (#1039). */
@RunWith(RobolectricTestRunner::class)
class TvFormNoteTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    private fun liveRegion(mode: LiveRegionMode) = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, mode)

    @Test
    fun `an error note is an assertive live region, and the others are polite`() {
        composeTestRule.setContent {
            BingeTvTheme {
                Column {
                    TvFormNote("Went wrong", tone = TvFormNoteTone.Error)
                    TvFormNote("Worked", tone = TvFormNoteTone.Success)
                    TvFormNote("Heads up", tone = TvFormNoteTone.Neutral)
                }
            }
        }

        composeTestRule.onNodeWithText("Went wrong").assert(liveRegion(LiveRegionMode.Assertive))
        composeTestRule.onNodeWithText("Worked").assert(liveRegion(LiveRegionMode.Polite))
        composeTestRule.onNodeWithText("Heads up").assert(liveRegion(LiveRegionMode.Polite))
    }
}
