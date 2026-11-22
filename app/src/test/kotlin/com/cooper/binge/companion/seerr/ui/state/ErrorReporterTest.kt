package com.cooper.binge.companion.seerr.ui.state

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.binge.designsystem.R as DesR

/** A failure with a cause and a next step is a dialog with one OK; any other is a snackbar. */
@RunWith(RobolectricTestRunner::class)
class ErrorReporterTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun show(error: SeerrError) {
        rule.setContent {
            BingeExpressiveTheme {
                val snackbar = remember { SnackbarHostState() }
                val reporter = rememberErrorReporter(snackbar)
                LaunchedEffect(Unit) { reporter.report(error) }
                SnackbarHost(snackbar)
            }
        }
    }

    @Test
    fun `a failure that needs a next step is a dialog with one OK and no snackbar`() {
        show(SeerrError.Unauthorized)
        rule.onNodeWithText(string(SeerrError.Unauthorized.titleRes())).assertExists()
        rule.onAllNodesWithText(string(SeerrError.Unauthorized.messageRes())).assertCountEquals(1)
        rule.onNodeWithText(string(DesR.string.action_ok)).performClick()
        rule.onAllNodesWithText(string(SeerrError.Unauthorized.titleRes())).assertCountEquals(0)
    }

    @Test
    fun `any other failure is a snackbar of its usual message and no dialog`() {
        show(SeerrError.Unreachable)
        rule.onNodeWithText(string(SeerrError.Unreachable.messageRes())).assertExists()
        rule.onAllNodesWithText(string(DesR.string.action_ok)).assertCountEquals(0)
    }
}
