package io.github.scottcooper92.binge.seerr.ui.settings

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.component.ItemRows
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A switch row tells a screen reader its state. The assertions are on the merged node a user meets:
 * its role and its [ToggleableState], not on pixels, which the screenshot baselines own.
 */
@RunWith(RobolectricTestRunner::class)
class SwitchRowSemanticsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    @Test
    fun `an editor toggle is one Switch node that reports on and off`() {
        var checked by mutableStateOf(true)
        composeTestRule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                ItemRows(rows = listOf(editorToggle(Icons.Filled.Vibration, "Trust proxy", checked) { checked = it }))
            }
        }
        composeTestRule.onNodeWithText("Trust proxy").assert(isSwitch).assertIsOn()
        composeTestRule.onNodeWithText("Trust proxy").performClick()
        assertEquals(false, checked)
        composeTestRule.onNodeWithText("Trust proxy").assertIsOff()
    }

    @Test
    fun `a disabled editor toggle does not act on a tap`() {
        var taps = 0
        composeTestRule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                ItemRows(rows = listOf(editorToggle(Icons.Filled.Vibration, "Trust proxy", true, enabled = false) { taps++ }))
            }
        }
        composeTestRule.onNodeWithText("Trust proxy").assert(isSwitch).performClick()
        assertEquals(0, taps)
    }

    @Test
    fun `the app settings switches expose their state`() {
        composeTestRule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                val app = AppSettings(bugReportUrl = "", shakeToReport = true, shareUsageData = false, sendCrashReports = true)
                ItemRows(rows = appRows(app, {}, {}, {}))
            }
        }
        composeTestRule.onNodeWithText(context.getString(R.string.settings_shake_to_report)).assert(isSwitch).assertIsOn()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_share_usage_data)).assert(isSwitch).assertIsOff()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_send_crash_reports)).assert(isSwitch).assertIsOn()
    }
}
