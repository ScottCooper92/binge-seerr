package io.github.scottcooper92.binge.seerr.ui.state

import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** `downloadEtaLabel`: minutes under an hour, whole hours from an hour up, rounded at the half hour. */
@RunWith(RobolectricTestRunner::class)
class DownloadEtaTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    /** Every ETA in [minutes] labelled in one composition, since a rule sets its content once. */
    private fun labels(vararg minutes: Int): List<String> {
        var shown = emptyList<String>()
        composeTestRule.setContent { shown = minutes.map { downloadEtaLabel(it) } }
        composeTestRule.waitForIdle()
        return shown
    }

    @Test
    fun `under an hour reads in minutes, and from an hour up in hours rounded at the half hour`() {
        assertEquals(
            listOf(
                "About 0 min left",
                "About 59 min left",
                "About 1 hr left",
                "About 1 hr left",
                "About 2 hr left",
            ),
            labels(0, 59, 60, 89, 90),
        )
    }
}
