package com.cooper.binge.companion.seerr.ui.handoff

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** #941 for the report a TV's code opens: the form tells the sheet when its message holds text not yet sent. */
@RunWith(RobolectricTestRunner::class)
class ReportTitleUnsentTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `a typed message is reported as unsent to the sheet`() {
        var unsent: Boolean? = null
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                ReportTitleContent(
                    state = ReportTitleUiState.Ready(title = "Dune", is4k = false, mediaId = 7),
                    actions = ReportTitleActions(),
                    onUnsentChange = { unsent = it },
                )
            }
        }
        rule.waitForIdle()
        assertEquals(false, unsent)

        rule.onNode(hasSetTextAction()).performTextInput("Subtitles out of sync")
        rule.waitForIdle()
        assertEquals(true, unsent)
    }
}
