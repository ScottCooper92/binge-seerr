package com.cooper.binge.companion.seerr.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.component.ItemRows
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.cooper.homelab.core.uiandroid.R as CoreR

@RunWith(RobolectricTestRunner::class)
class AboutRowsTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    @Test
    fun `the About group lists its rows and the licences and diagnostics rows open their pages`() {
        var licences = 0
        var diagnostics = 0
        composeTestRule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                ItemRows(rows = aboutRows(onOpenLicences = { licences++ }, onOpenDiagnostics = { diagnostics++ }))
            }
        }
        listOf(
            string(R.string.about_app_version),
            string(R.string.about_privacy_policy),
            string(R.string.licences_title),
            string(CoreR.string.diagnostics_title),
            string(R.string.about_join_beta),
        ).forEach { composeTestRule.onNodeWithText(it).assertIsDisplayed() }
        composeTestRule.onNodeWithText(string(R.string.licences_title)).performClick()
        assertEquals(1, licences)
        composeTestRule.onNodeWithText(string(CoreR.string.diagnostics_title)).performClick()
        assertEquals(1, diagnostics)
    }
}
