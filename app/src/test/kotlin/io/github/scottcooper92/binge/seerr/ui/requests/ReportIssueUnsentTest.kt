package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * #941: the report sheet locks and asks before closing while its message holds text not yet sent. What it reads is
 * whether the form holds any; the lock and the question are checked on a device (Back in a sheet's own window).
 */
@RunWith(RobolectricTestRunner::class)
class ReportIssueUnsentTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `a message is unsent until it is blank again or sent`() {
        var report by mutableStateOf<IssueReport>(IssueReport.Idle)
        var unsent: Boolean? = null
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                ReportIssueContent(report = report, onSend = { _, _ -> }, onUnsentChange = { unsent = it })
            }
        }
        rule.waitForIdle()
        assertEquals(false, unsent)

        rule.onNode(hasSetTextAction()).performTextInput("Audio drops out")
        rule.waitForIdle()
        assertEquals(true, unsent)

        rule.onNode(hasSetTextAction()).performTextReplacement("  ")
        rule.waitForIdle()
        assertEquals("blank is nothing to lose", false, unsent)

        rule.onNode(hasSetTextAction()).performTextReplacement("Audio drops out")
        report = IssueReport.Sent
        rule.waitForIdle()
        assertEquals("a sent report is nothing to lose", false, unsent)
    }
}
